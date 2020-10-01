# Running Nussknacker with older Flink versions
Standard [Nussknacker](https://github.com/touk/nussknacker) distribution works for (more or less) latest Flink version. 
However, Flink upgrade can be fairly complex process for large deployments. Most of Nussknacker code is based on stable Flink API, so changes required to run against older Flink version are usually relatively small. 

This repository contains code that can be used to prepare custom [model](https://nussknacker.io/API.html) and (process engine)[https://nussknacker.io/Engines.html] and run older Flink cluster (e.g. 1.9) with newest Nussknacker. 

Each supported Flink version (e.g. 1.9) comes with two modules:
- model - additional classes/changes that need to be put into model
- manager - changes which have to be made in ProcessManager

Following tests will be provided to check if all needed changes are found:
- GenericItSpec based on [generic model test](https://github.com/TouK/nussknacker/blob/staging/engine/flink/generic/src/test/scala/pl/touk/nussknacker/genericmodel/GenericItSpec.scala)
- TODO: integration test to check ProcessManager implementation
