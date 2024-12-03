# Running Nussknacker with older Flink versions

[Nussknacker](https://github.com/touk/nussknacker) distribution works with (more or less) the latest Flink version.
Nussknacker mostly uses the stable Flink API. Thanks to this, upgrading Nussknacker to the version that is associated with the Flink libraries bump
can usually be done in two steps:
1. Upgrade of Nussknacker, redeploy of all scenarios 
2. Upgrade of Flink

However, sometimes due to the use of the new Flink API in new Nussknacker version or due to incompatible changes in the Flink API,
it is necessary to make changes in the distribution to allow this two-step upgrade.

In this repository, you'll find the source code of the compatibility layers needed in such a situation. Binaries are published to Maven Central.
The repository usually contains compatibility layers that were tested with the last Flink version before the last incompatible change.
These layers may also work with earlier Flink versions, but there is no guarantee.
For this reason, we recommend that you always upgrade Flink to the latest version supported by Nussknacker.

## Flink 1.18

Currently, the repository contains the source code of compatibility layers needed to run Nussknacker >= 1.18 with Flink 1.18:
* `nussknacker-flink-compatibility-1-18-model` - extension that should be added in model classpath 
* `nussknacker-flink-compatibility-1-18-kafka-components` - extension that should be used as a replacement of `flinkKafka.jar` component provided

Full list of changes that should be done in distribution:
* [nussknacker-flink-compatibility-1-18-kafka-components_2.12-1.0.0-nu1.18-assembly.jar](https://repo1.maven.org/maven2/pl/touk/nussknacker/nussknacker-flink-compatibility-1-18-kafka-components_2.12/1.0.0-nu1.18/nussknacker-flink-compatibility-1-18-kafka-components_2.12-1.0.0-nu1.18-assembly.jar)
  should be placed in model classpath
* jar with deployment manager (todo: add link when published) should be placed in `/managers` dir and exiting flink deployment managers jars have to be deleted (`development-tests-manager.jar`, `nussknacker-flink-periodic-manager.jar`) 
* `flinkKafka.jar` should be removed from model classpath
* `NU_DISABLE_FLINK_TYPE_INFO_REGISTRATION` environment variable should be set to `true` in Designer, Flink Job Manager and Flink Task Manager

In the [Pull Request available in nussknacker-quickstart project](https://github.com/TouK/nussknacker-quickstart/pull/195/files) you can check how such setup can be prepared

The upgrade procedure:
1. Changes in Nussknacker distribution described above
2. Upgrade of Nussknacker to 1.18 version, redeploy of all scenarios
3. Upgrade of Flink to 1.19 version
4. Revert of changes introduced in step 1., another redeploy of all scenarios

## Changelog

### 1.0.2

* PreFlink119TypeInformationDetection registration has been removed due to fixup to redeployments in [core nussknacker](https://github.com/TouK/nussknacker/pull/7270) 

### 1.0.1

* Bump Nussknacker from preview version to release 1.8.0 version

### 1.0.0

* Compatibility layers allowing to use Nussknacker 1.18 with Flink 1.18
