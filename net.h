#ifndef NET_H
#define NET_H

#include <winsock2.h>

int net_initialize(void);
void net_cleanup(void);

SOCKET net_create_server_socket(int port);
SOCKET net_accept_client(SOCKET serverSock);
int net_set_timeout(SOCKET sock, int seconds);
int net_send_text(SOCKET sock, const char *text);
int net_send_line(SOCKET sock, const char *text);
int net_recv_line(SOCKET sock, char *buffer, int maxLen);
void net_close_socket(SOCKET sock);

#endif /* NET_H */

