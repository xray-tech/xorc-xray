(defproject io.xorc/re "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [
                 ;; conflicts
                 [clj-time "0.14.4"]
                 [commons-codec "1.11"]
                 [joda-time "2.10"]

                 [org.clojure/clojure "1.9.0"]
                 [duct/core "0.6.2"]
                 [duct/module.web "0.6.4"]
                 [duct/module.ataraxy "0.2.0"]
                 [duct/module.logging "0.3.1"]
                 [clojusc/protobuf "3.5.1-v1.1"]
                 [com.google.protobuf/protobuf-java-util "3.6.0"]
                 [org.clojure/core.async "0.4.474"]
                 [com.datastax.cassandra/cassandra-driver-core "3.5.1"]
                 [org.apache.kafka/kafka-clients "1.1.1"]
                 [org.apache.kafka/kafka-streams "1.1.1"]
                 [io.prometheus/simpleclient_common "0.4.0"]
                 [io.prometheus/simpleclient_hotspot "0.4.0"]
                 [io.prometheus/simpleclient_jetty "0.4.0"]
                 [org.clojure/core.cache "0.7.1"]

                 [io.xorc/oam "1.1-SNAPSHOT"]
                 [cc.qbits/alia-all "4.2.2"]
                 [cc.qbits/hayt "4.0.1"]
                 [com.cemerick/pomegranate "1.0.0"]
                 [environ "1.1.0"]
                 [clj-http "3.9.1"]
                 [slingshot "0.12.2"]

                 ;; logging
                 [org.clojure/tools.logging "0.4.1"]
                 [org.slf4j/slf4j-api "1.7.25"]
                 [org.slf4j/log4j-over-slf4j "1.7.25"]
                 [org.slf4j/jcl-over-slf4j "1.7.25"]
                 [org.slf4j/jul-to-slf4j "1.7.25"]
                 [ch.qos.logback/logback-classic "1.2.3"]
                 [ch.qos.logback.contrib/logback-json-classic "0.1.5"]
                 [ch.qos.logback.contrib/logback-jackson "0.1.5"]

                 ;; tracing
                 [io.jaegertracing/jaeger-client "0.30.3"]
                 [io.opentracing.contrib/opentracing-cassandra-driver "0.0.6"]
                 [io.opentracing.contrib/opentracing-api-extensions "0.1.0"]
                 [io.opentracing.contrib/opentracing-api-extensions-tracer "0.1.0"]

                 ;; metrics
                 [io.micrometer/micrometer-core "1.0.6"]
                 [io.micrometer/micrometer-registry-prometheus "1.0.6"]]
  :plugins [[duct/lein-duct "0.10.6"]]
  :main ^:skip-aot re.main
  :resource-paths ["resources" "target/resources"]
  :prep-tasks     ["javac" "compile"]
  :java-source-paths ["java" "../proto/events"]
  :repositories [["jcenter" {:url "https://jcenter.bintray.com"}]]
  :profiles
  {:dev  [:project/dev :profiles/dev]
   :repl {:prep-tasks   ^:replace ["javac" "compile"]
          :repl-options {:init-ns user}}
   :uberjar {:aot :all}
   :project/dev  {:source-paths   ["dev/src"]
                  :resource-paths ["dev/resources"]
                  :dependencies   [[integrant/repl "0.3.1"]
                                   [eftest "0.5.2"]
                                   [kerodon "0.9.0"]
                                   [org.clojure/tools.nrepl "0.2.13" :exclusions [org.clojure/clojure]]
                                   [com.gearswithingears/shrubbery "0.4.1"]
                                   [org.apache.kafka/kafka-streams-test-utils "1.1.0"]]
                  :plugins [[refactor-nrepl "2.4.0-SNAPSHOT"]
                            [cider/cider-nrepl "0.18.0-SNAPSHOT"]
                            [com.jakemccrary/lein-test-refresh "0.22.0"]
                            [lein-cloverage "1.0.13"]]
                  :java-source-paths ["../proto/test/proto_edn"]
                  :jvm-opts ["-DJAEGER_SERVICE_NAME=re"
                             "-DJAEGER_ENDPOINT=http://jaeger:14268/api/traces"
                             "-DJAEGER_SAMPLER_PARAM=1"]}})
