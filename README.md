# change-o-matic
Metalab coin-change machine (using the Payout JSON API)

- [Source](https://github.com/metalab-kassomat/kassomat-changeomatic)
- Written in Java, can be built (and run) with [Maven](https://maven.apache.org/)
  - build: <code>mvn install</code>
  - run: <code>mvn exec:java -Dexec.mainClass="at.metalab.changeomatic.ChangeomaticMain"</code>
- UI: Graphical Swing Frontend
- libs
  - [Redisson](http://redisson.org/) (Redis client)
  - [Jackson](https://github.com/FasterXML/jackson) (JSON library)

![screenshot](https://github.com/metalab-kassomat/kassomat-changeomatic/blob/master/changeomatic.png)

#### Example trace, showing the exchange of a 10 Euro note 
```
"PUBLISH" "validator-event" "{\"event\":\"reading\"}"
"PUBLISH" "validator-event" "{\"event\":\"reading\"}"
"PUBLISH" "validator-event" "{\"event\":\"read\",\"amount\":1000,\"channel\":2}"
"PUBLISH" "validator-event" "{\"event\":\"stacking\"}"
"PUBLISH" "validator-event" "{\"event\":\"stacking\"}"
"PUBLISH" "validator-event" "{\"event\":\"credit\",\"amount\":1000,\"channel\":2}"
"PUBLISH" "hopper-request" "{\"cmd\":\"do-payout\",\"msgId\":\"de21cbee-7bef-4786-935a-0e84231ce916\",\"amount\":1000}"
"PUBLISH" "hopper-response" "{\"correlId\":\"de21cbee-7bef-4786-935a-0e84231ce916\",\"result\":\"ok\"}"
"PUBLISH" "hopper-event" "{\"event\":\"dispensing\",\"amount\":0}"
"PUBLISH" "validator-event" "{\"event\":\"stacking\"}"
"PUBLISH" "validator-event" "{\"event\":\"stacked\"}"
"PUBLISH" "hopper-event" "{\"event\":\"dispensing\",\"amount\":0}"
"PUBLISH" "hopper-event" "{\"event\":\"dispensing\",\"amount\":100}"
"PUBLISH" "hopper-event" "{\"event\":\"dispensing\",\"amount\":900}"
"PUBLISH" "hopper-event" "{\"event\":\"dispensing\",\"amount\":1000}"
"PUBLISH" "hopper-event" "{\"event\":\"cashbox paid\",\"amount\":0,\"cc\":\"EUR\"}"
"PUBLISH" "hopper-event" "{\"event\":\"dispensed\",\"amount\":1000}"
```
