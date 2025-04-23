# 潮韵茶社

本项目是使用SpringBoot框架开发的一个校内在线饮品订购系统

## 技术栈

- **后端框架**

  核心框架：Spring Boot 2.7.3

  持久层框架：MyBatis

  安全认证：JWT

  缓存框架：Redis + Redisson

  接口文档：SpringDoc/APIfox

- **数据库和存储服务**

  关系型数据库：MySQL

  连接池：Durid

  对象存储：阿里云OSS

  缓存数据库：Redis

- **前端框架**

  Vue

  Uniapp

  ElementUI

- **前后端通信**

  通信协议：RESTful API

  实时通信：WebSocket

- **其他功能**

  Excel处理：Apache POI

  支付功能：微信API

  静态资源服务器：Ngnix

## Windos开发环境搭建

1. 安装 Java JDK 17 并配置环境变量
2. 安装 MySQL、Redis 数据库并创建相应数据库
   - 创建 MySQL 数据库与表: 运行 [create.sql](https://github.com/Marisalice114/tidevibe/blob/master/tide-server/src/main/resources/create.sql)
3. 安装 Maven 构建工具
4. 下载安装 Nginx 并完成以下配置

```java
# 在 http 这一项下配置以下内容

map $http_upgrade $connection_upgrade{
	default upgrade;
	'' close;
}

upstream webservers{
  server 127.0.0.1:8080 weight=90 ;
  #server 127.0.0.1:8088 weight=10 ;
}

server {
    listen       80;
    server_name  localhost;

    location / {
        root   html/sky;
        index  index.html index.htm;
    }

    # 反向代理,处理管理端发送的请求
    location /api/ {
		proxy_pass   http://localhost:8080/admin/;
        #proxy_pass   http://webservers/admin/;
    }

	# 反向代理,处理用户端发送的请求
    location /user/ {
        proxy_pass   http://webservers/user/;
    }

	# WebSocket
	location /ws/ {
        proxy_pass   http://webservers/ws/;
		proxy_http_version 1.1;
		proxy_read_timeout 3600s;
		proxy_set_header Upgrade $http_upgrade;
		proxy_set_header Connection "$connection_upgrade";
    }

}
```

5. 克隆项目到本地 ``` git clone https://github.com/Marisalice114/tidevibe.git``` 

6. 下载cpolar，并获取内网穿透地址

7. 修改配置文件 [application-dev.yml](https://github.com/Marisalice114/tidevibe/blob/master/tide-server/src/main/resources/application-dev.yml)

``` java
sky:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    host: localhost
    port: 3306
    database:数据库名称
    username:数据库用户
    password:密码
  alioss:
    endpoint:阿里云OSS的服务端点地址
    bucketName:自定义的容器名
    region:OSS服务所在的数据中心区域
  redis:
    host: localhost
    port: 6379
    database: 9
  wechat:
    appid: 申请微信小程序可获得
    secret: 申请微信小程序可获得
    # 添加模拟支付所需的配置，这些在开发环境中不会实际使用
    # 但是为了避免空指针异常，还是需要提供这些值
    mchid: mock_mch_id
    mchSerialNo: mock_serial_no
    privateKeyFilePath: mock/path/to/private/key.pem
    weChatPayCertFilePath: mock/path/to/cert.pem
    notifyUrl: 内网穿透地址/notify/paySuccess
    refundNotifyUrl: 内网穿透地址/notify/refundSuccess
```

8. 运行项目中tide-ngnix下的[nginx.exe](https://github.com/Marisalice114/tidevibe/blob/master/tide-ngnix/nginx-1.20.2-firmament/nginx.exe)

9. 运行项目，打开页面
