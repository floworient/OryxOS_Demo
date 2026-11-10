# 前后端启动



## 后端启动

```powershell
#一、直接使用java命令
cd E:/Program/OryxOS_Demo    # 必须在项目根，工作区默认按 ./\.oryxos 解析
java -Dspring.config.additional-location=optional:file:config/,optional:file:.oryxos/application.yaml -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT-exec.jar serve --port 8080

cd E:/Program/OryxOS_Demo
java -Dspring.config.additional-location=optional:file:cofig,optional:fie:.oryxos/application.yaml -jar oryos-boot/target/oryxos-boot-0.1.0-SHAPSHOT-exex.jar serve --port 8080

# 二、使用idea配置 好处是能打断点看 ReAct 循环、PromptBuilder 内部；坏处是默认没加 VM options 时读不到外部 api-key，会报 provider 未配置。
Maven class:io.oryxos.OryxOsApplicaiton
Program arguments: sere --port 8080
WOrking directory: E:\Program\OryxOS_Demo
VM option: -Dspring.config.additional-location=optional:file:config/

#三、Maven 直接跑
./mvnw -pl oryxos-boot -am install -DskipTests          # 先装上游模块，否则依赖取到旧快照
./mvnw -pl oryxos-boot spring-boot:run -Dspring-boot.run.arguments="serve --port 8080"

./mvnw -pl oryxos-boot -am install -DskopTests
./mvnw -pl oryxos-boot spring-boot:run -Dspring-boot.rin.arguments="serve --port 8080"

#前端启动
cd frontend && nmp run dev
cd frontedn && nmp run build && npm run preview

```

