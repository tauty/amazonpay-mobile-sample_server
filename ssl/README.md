# SSL自己証明書の生成方法

## 前提条件
- opensslがインストールされていること (されていない場合には、各環境に合わせてインストール)
- 下記内容の「v3.ext」というファイルをcurrent directoryに用意すること
```
[req]
distinguished_name=req_distinguished_name
[req_distinguished_name]
[SAN]
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid:always,issuer:always
basicConstraints=CA:TRUE
subjectAltName=IP:10.0.2.2
```

## 生成コマンド
```sh
# sample.key, sample.crtの生成
openssl req -x509 -nodes -days 3650 -newkey rsa:2048 \
  -keyout sample.key -out sample.crt \
  -subj "/C=JP/ST=Tokyo/L=Tokyo/O=Sample Inc./CN=localhost" \
  -reqexts v3_req -reqexts SAN -extensions SAN -config v3.ext

# sample.crtの確認
openssl x509 -in sample.crt -text -noout

# sample.crtをDER形式へ変換してsample.der.crtを生成(Android端末へのインストール用)
openssl x509 -in sample.crt -outform der -out sample.der.crt

# sample.p12の生成(本サンプルのserver側への設定用)
openssl pkcs12 -export -in sample.crt -inkey sample.key -out sample.p12
#   passwordの設定が求められる。設定した値は「./src/main/resources/application.properties」の「server.ssl.key-store-password」に反映する。
```

## Note
上記の各コマンドで、生成されるファイル名の部分は変更しても良いが、「sample.p12」を変更した場合には「./src/main/resources/application.properties」の「server.ssl.key-store」に反映すること。
