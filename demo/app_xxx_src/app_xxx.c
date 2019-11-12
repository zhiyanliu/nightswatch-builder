#include <signal.h>
#include <sys/socket.h>
#include <linux/limits.h>
#include <sys/types.h>
#include <netinet/in.h>
#include <netdb.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <errno.h>
#include <arpa/inet.h> 


void sig_handler(int signo)
{
    exit(0);
}

int main(int argc, char *argv[])
{
    int sockfd = 0, n = 0, app_name_l = 0, topic_l = 0;
    char app_name[PATH_MAX + 1], topic[PATH_MAX + 50];
    struct sockaddr_in serv_addr; 
//    char *data = "{\"payload\": \"*,*,red\"}\n",
    char *data = "*,*,red\n",
        *log = "LOG: send data to IoT Core successfully.\n",
        conn_err[256], sock_err[256];

//    signal(SIGINT, sig_handler);

    sockfd = socket(AF_INET, SOCK_STREAM, 0);
    if (-1 == sockfd) {
        snprintf(sock_err, 256, "LOG: failed to create socket fd: %d.\n", errno);
        write(2, sock_err, strlen(sock_err));
        return 1;
    }

    memset(&serv_addr, '\0', sizeof(serv_addr)); 

    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(9000); 

    inet_pton(AF_INET, argv[1], &serv_addr.sin_addr);

    if (-1 == connect(sockfd, (struct sockaddr *)&serv_addr, sizeof(serv_addr))) {
        snprintf(conn_err, 256, "LOG: failed to connect to IPC server: %d.\n", errno);
        write(2, conn_err, strlen(conn_err));
        return 1;
    }

    app_name_l = snprintf(app_name, PATH_MAX + 1, "%s\n", argv[2]);
    write(sockfd, app_name, app_name_l);

    topic_l = snprintf(topic, PATH_MAX + 50, "%s\n", argv[3]);
    write(sockfd, topic, topic_l);

    while (1)
    {
        write(sockfd, data, strlen(data));
        write(1, log, strlen(log));
        sleep(5);
    } 

    return 0;
}

