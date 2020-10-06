To make current Nussknacker work with Flink 1.9 minor changes has to be made. Relevant PRs:
- https://github.com/TouK/nussknacker/pull/873
- https://github.com/TouK/nussknacker/pull/1044

There is also one tricky and not obvious issue, not visible by looking at NK source code.
Interfaces ```SerializationSchema``` and ```DeserializationSchema``` got new default methods in Flink 1.11, 
which use new ```InitializationContext``` interface. It causes serialization problems, as this interface
is in bytecode and is not present in Flink 1.9. Therefore, we add missing interfaces here. 
This is a bit of a hack, but should be enough to work.

