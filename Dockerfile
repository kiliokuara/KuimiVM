FROM openjdk:18-bullseye AS builder

ADD ./ /build
ADD ./BasePath.java /build/tencent/src/main/java/tencentlibfekit/
WORKDIR /build
RUN chmod +x ./gradlew
RUN ./gradlew --no-daemon
RUN ./gradlew --no-daemon :packer:build


FROM openjdk:18-bullseye
RUN mkdir /app
WORKDIR /app
COPY --from=builder /build/packer/build/proguard/server-remap.jar /app/server.jar

# 允许使用 docker cp 获取 console 插件
COPY --from=builder /build/packer/build/proguard/vivo50-kfc-code45-rpc.jar /app

ENV SERVER_IDENTITY_KEY=vivo50
ENV AUTH_KEY=kfc
# 因为兼容性原因改成host和port
ENV HOST=0.0.0.0
ENV PORT=8888

EXPOSE 8888

CMD java -jar /app/server.jar


