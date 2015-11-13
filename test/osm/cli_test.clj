(ns osm.cli-test
  (:use osm.cli)
  (:use midje.sweet))

(fact "CLI interface work"
  (-main)
  (-main "-h")
  (-main "-i")
  (-main "-i" "not")
  (-main "-o" "data/tmp/demo.geo.json")
  (-main "-i" "test-data/demo.osm.bz2" "-o" "data/tmp/cli.geo.json")
  #_(-main "-i" "test-data/demo.osm.bz2") 
      )