FROM alpine:latest
LABEL maintainer="neoandrey@yahoo.com"
COPY entrypoint.sh /
RUN apk add --update --no-cache openssh \ 
&& echo 'PasswordAuthentication yes' >> /etc/ssh/sshd_config && mkdir -p /opt/data \
&& adduser -h /home/sftpuser -s /bin/sh -D sftpuser \
&& echo -n 'sftpuser:SftpUser123!' | chpasswd \
&& chmod +x -v entrypoint.sh 
EXPOSE 22
ENTRYPOINT ["/entrypoint.sh"]
