# RuleEngine

RuleEngine is a coordinator for OAM programs and the external world.

## Architecture

RuleEngine is built on top of [Duct Framework](https://github.com/duct-framework/duct) and uses extremely [Integrant](https://github.com/weavejester/integrant) configuration system.

Components of engine are modular and decopled from each other. Let's look at current pipeline of message handling:

```
kafka-consumer -> decoder -> crm -> oam -> coeffects -> persistence
```

This pipeline is assembled on configuration stage, each step change/extend message or executes side effects and call next one. Because configuration is just data we can build tools to assemble systems, even programmatically (web UI?), including set of supported coeffects. We can think about it as "Building an Environment" for OAM programs.

Running systems don't have any global state and it's possible to run different systems inside single JVM instance. It's useful at least for development (run tests and dev versions at the same time from the REPL), but maybe in the future, we'll find more cases.

### Threading model

All computations are executed inside kafka-consumer thread pool. This is simple approach and fits well Kafka messaging platform, but maybe not the best from the throughput perspective.

## Main components

- `:re/oam` provides :run and :unblock functions to execute OAM programs
- `:re/kafka-consumer` consumes kafka topics and call `next` on each message
- `:re.stage/decoder` parses RoutableMessages, replaces :value of the message and call `next`
- `:re.stage/crm` contains business logic, it manages entities, knows which programs should be run, unblocks corresponding programs
- `:re.stage/rpc` receives RPC responses for particular states and unblock them
- `:re.stage/coeffects` for each coeffect in the message it runs corresponding coeffect's handler
- `re.stage/persistence` it saves state to cassandra if the program still running or delete the record if not
- `re.coeffect/http` `event` coeffect handler, sends event to kafka topic using `encoder`
- `re.coeffect/timer-service` `timer` coeffect handler, manages local timeout service (without persistence) and sends TimerExpired events to `kafka`
- `:re/encoder` provides a function to encode the message as protobuf 

## `data` structure

- `:core/now` "current" time
- `:core/binary-message`
- `:core/message`
- `:core/state-id` 
- `:core/state` 
- `:core/state-meta`
- `:core/program`
- `:core/effects` sets of effects to be executed by :leave of stage.effects
- `:oam/values`
- `:oam/coeffects`
- `:oam/state`
- `:oam/bc`
- `:oam/coeffect-id`
- `:oam/coeffect-value`
- `:crm/entity`
- `:crm/campaign`
- `:kafka/key`
- `:kafka/offset`
- `:kafka/partition`
- `:kafka/topic`


## Installing


```bash
git clone https://github.com/xray-tech/xorc-xray.git --recursive
```

- Setup docker authentication as described in the [Infra README](../infra/README.md)

## Testing


To execute tests:

```bash
docker-compose run re lein test
```

To execute tests continuously:

```bash
docker-compose run re lein test-refresh
```

## Running

```bash
# If the compiler was modified
docker-compose build

# If a new Kotlin OAM jar was published
cd re; lein -U deps

# run
docker-compose -f docker-compose.yml
```

## REPL

While it's still possible to have "normal" development via `cider-jack-in` (or analogs), to have all required external dependencies (protobuf, kafka, scylladb) just in place you can start headless REPL with docker-compose and connect to it with your editor:

```bash
docker-compose -f docker-compose.yml -f docker-compose.repl.yml up
```

The main drawback of this approach, that goto-* functionality doesn't work. I currently don't have an easy solution for this.

`dev` namespace contains useful functions to play with system, like `add-program` and `send-event`


### Quickstart

Connect to the nREPL via [your IDE](https://cursive-ide.com/) or command line and start and reset the dev namespace

```bash
lein repl :connect localhost:4011
(dev) (reset)
```



**Load a compiled orc program**

This program ([source here](docs/programs/subscription.orc)) listens to a `checkout` event and prints the number of occurences for each entity.

To add it to the `dev` universe

```clojure
;; Add "re/oam/subscription.oam" program to dev universe
;; See re/docs/programs/subscription.orc for source code
(add-program "dev" "re/oam/subscription.oam")
```

and start the program for each entity in that universe

```clojure
;; It starts all active programs in dev universe.
(send-event "dev" "events.SDKEvent" {"header" {"type" "events.SDKEvent"
                                               "recipientId" "dev-entity"
                                               "createdAt" 0
                                               "source" "repl"}
                                     "event" {"name" "dev-event"}
                                     "environment" {"appId" "dev"}})
```   

**Send events**

You can then send an event using `send-event` and specify the `recipientId` for which entity you want the program to run.

```clojure                                    
;; subscription.oam subscribes to {events\.SDKEvent}{checkout} events, 
;; any other events will be ignored
(send-event "dev" "events.SDKEvent" {"header" {"type" "events.SDKEvent"
                                               "recipientId" "dev-entity"
                                               "createdAt" 0
                                               "source" "repl"}
                                     "event" {"name" "checkout"
                                              "properties" [{"key" "sum"
                                                             "numberValue" 1000}]}
                                     "environment" {"appId" "dev"}})
```

In the docker logs, you'll see the output each time you send the event for a given entity

```
re_1         | 09:32:47.398 INFO  [] re.stage.log-oam-values - Value iteration: 4; sum: 1000.0
```

**Run Orc directly**

You can `POST` a orc source direclty to `'http://localhost:4012/run` to run it

```bash
curl -i -X POST -H "Content-Type:application/orc" -d '1 | 2' 'http://localhost:4012/run'

{"values":[1,2]}
```

or using a file with a program that will run:

```bash
curl  -i -X POST -H "Content-Type:application/orc" --data-binary @re/docs/programs/bench.orc 'http://localhost:4012/run'


{"values":[0],"state":"876e8ecd-231a-4d21-a9fd-df01d4e0d8ee"}
```
    
Note the state id    
    
### Program Tracing

The [Jeager UI](https://github.com/jaegertracing/jaeger-ui) is accessible on `http://localhost:16686/`                           

You can filter the traces using the state id of your program (see above) by using the tag filter

```
re.state=876e8ecd-231a-4d21-a9fd-df01d4e0d8ee
```
 
### Local OAM development

To use a local version of [JVM OAM](https://github.com/xray-tech/xorc-kotlin-oam) install it to a local maven repository (`./gradlew :runtime:install`) and restart xray

