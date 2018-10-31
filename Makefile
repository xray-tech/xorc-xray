.PHONY: prepare

prepare:
	protoc --java_out=./java/events -I events/schema events/schema/**/*.proto
	protoc --java_out=./test/proto_edn/java test/proto_edn/test.proto
