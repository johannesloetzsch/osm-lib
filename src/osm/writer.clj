(ns osm.writer
  (:use osm.reader)
  (:use batcher.core)
  (:require [clojure.core.async :refer [<! <!! >! >!! chan close! go-loop]])
  (:require [clj-http.lite.client :as http])
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]))

(defn matcher
  [hashmap] 
  (fn [obj]
    (every?
      (fn [kv]
        (= (get (:properties obj) (key kv))
           (val kv)))
      hashmap)))

(defn all
  [_] true)

(defn spit-each
  "Spit(write) a feature to a dir"
  ([file dest] (spit-each file dest false))
  ([file dest swap] (spit-each file dest swap nil))
  ([file dest swap f]
   (let [dir (io/file dest)
         match (if (nil? f) all (matcher f))]
     (if (not (.exists dir)) (.mkdir dir))
      ((if swap read-stream-swap read-stream) file
        (fn [feature]
         (if (match feature)
           (spit 
             (io/file dir (str (get-in feature [:properties :id]) ".geojson"))
             (json/write-str feature))))))))

(defn spit-each-swap
  "Spit(write) a feature to a dir, swapping"
  [file dest] (spit-each file dest true))

(defn spit-all
  "Spit whole XML as a FeatureCollection"
  ([file dest] (spit-all file dest false))
  ([file dest swap] (spit-all file dest swap nil))
  ([file dest swap f]
    (with-open [writer (io/writer dest)]
      (let [first? (atom true)
            match (if (nil? f) all (matcher f))]
        (.write writer "{\"type\":\"FeatureCollection\",\"features\":[")
        ((if swap read-stream-swap read-stream) file
          (fn [feature]
            (if (match feature)
              (if @first?
                (do (swap! first? (fn [a] false))
                    (.write writer (json/write-str feature)))
                (.write writer (str "," (json/write-str feature) "\n"))))))
        (.write writer "]}")))))

(defn spit-all-swap
  "Spit whole XML as a FeatureCollection, swapping to disk"
  ([file dest] (spit-all file dest true nil))
  ([file dest f] (spit-all file dest true f)))

(defn post-0
  [url data]
  (http/post url
    {:content-type :json
     :conn-timeout 15000
     :socket-timeout 15000
     :body (json/write-str {:type "FeatureCollection" :features data})}))

(defn post
  ([file url] (post file url 512 true))
  ([file url limit] (post file url limit true))
  ([file url limit swap]  (post file url limit swap nil))
  ([file url limit swap f] 
   (let [bat (batcher {:size limit :fn (partial post-0 url)})
        match (if (nil? f) all (matcher f))]
     ((if swap read-stream-swap read-stream) 
      file
      (fn [feat]
        (if (match feat)
          (>!! bat feat))))
     (Thread/sleep 1000)
     (close! bat))))

