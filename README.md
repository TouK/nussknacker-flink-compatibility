# Running Nussknacker with older Flink versions
Standard [Nussknacker](https://github.com/touk/nussknacker) distribution works for (more or less) latest Flink version. 
However, Flink upgrade can be fairly complex process for large deployments. Most of Nussknacker code is based on stable Flink API, so changes required to run against older Flink version are usually relatively small. 

This repository contains code that can be used to prepare custom [model](https://nussknacker.io/API.html) and (process engine)[https://nussknacker.io/Engines.html] and run older Flink cluster (e.g. 1.9) with newest Nussknacker. 
