# Running Nussknacker with older Flink versions

[Nussknacker](https://github.com/touk/nussknacker) distribution works with (more or less) latest Flink version.
Nussknacker mostly uses the stable Flink API. Thanks to that, upgrade of Nussknacker to version which is connected with Flink libraries bump
is usually possible to do in two steps:
1. Upgrade of Nussknacker, redeploy of all scenarios 
2. Upgrade of Flink

However, sometimes due to usage of the new Flink API in new Nussknacker version or an incompatible changes in Flink API,
it is necessary to apply changes in distribution, that make this two-step upgrade possible.

In this repository, you can find the source code of compatibility layers necessary in such situation. Binaries are published in maven central.
The repository usually contains compatibility layers tested with the last Flink version before the last incompatible change.
These layers might also work for previous Flink versions, but there is no guaranty for that.
Because of that we recommend to always upgrade Flink to the latest version supported by Nussknacker.

## Flink 1.18

Currently, the repository contains the source code of compatibility layers necessary to run Nussknacker >= 1.18 with Flink 1.18:
* `nussknacker-flink-compatibility-1-18-model` - extension that should be added in model classpath 
* `nussknacker-flink-compatibility-1-18-kafka-components` - extension that should be used as a replacement of `flinkKafka.jar` component provided

Full list of changes that should be done in distribution:
* [nussknacker-flink-compatibility-1-18-kafka-components_2.12-1.0.0-nu1.18-assembly.jar](https://repo1.maven.org/maven2/pl/touk/nussknacker/nussknacker-flink-compatibility-1-18-kafka-components_2.12/1.0.0-nu1.18/nussknacker-flink-compatibility-1-18-kafka-components_2.12-1.0.0-nu1.18-assembly.jar)
  should be placed in model classpath
* [nussknacker-flink-compatibility-1-18-model_2.12-1.0.0-nu1.18.jar](https://repo1.maven.org/maven2/pl/touk/nussknacker/nussknacker-flink-compatibility-1-18-model_2.12/1.0.0-nu1.18/nussknacker-flink-compatibility-1-18-model_2.12-1.0.0-nu1.18.jar)
  should be placed in model classpath
* `flinkKafka.jar` should be removed from model classpath
* `NU_DISABLE_FLINK_TYPE_INFO_REGISTRATION` environment variable should be set to `true` in Designer, Flink Job Manager and Flink Task Manager

In the [Pull Request available in nussknacker-quickstart project](https://github.com/TouK/nussknacker-quickstart/pull/195/files) you can check how such setup can be prepared

## Changelog

### 1.0.0

* Compatibility layers allowing to use Nussknacker 1.18 with Flink 1.18