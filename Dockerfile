FROM xorcio/orc:3146eb9aec551972679eba1fb5647af2128c6098 as orc
FROM brennovich/protobuf-tools:2.2.1 as proto

RUN mkdir -p java/events test/proto_edn/java
COPY events events
COPY test/proto_edn/test.proto test/proto_edn/test.proto

RUN protoc --java_out=./java/events -I events events/**/*.proto events/*.proto
RUN protoc --java_out=./test/proto_edn/java test/proto_edn/test.proto

FROM clojure:lein-2.8.1
RUN mkdir -p /usr/app
WORKDIR /usr/app
COPY --from=proto java/events ../proto/events 
COPY --from=proto test/proto_edn/java ../proto/test/proto_edn
COPY --from=orc /usr/local/bin/orc.exe /usr/local/bin/orc.exe
COPY --from=orc /prelude /orc/prelude

COPY project.clj /usr/app/

RUN lein deps

COPY . /usr/app

CMD ["lein", "repl", ":headless"]