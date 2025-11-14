#include "net.h"

#include <stdio.h>
#include <string.h>
#include <ws2tcpip.h>

int net_initialize(void)
{
    WSADATA wsaData;
    int res = WSAStartup(MAKEWORD(2, 2), &wsaData);
    if (res != 0)
    {
        fprintf(stderr, "WSAStartup falhou: %d\n", res);
        return 0;
    }
    return 1;
}

void net_cleanup(void)
{
    WSACleanup();
}

SOCKET net_create_server_socket(int port)
{
    SOCKET listenSock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (listenSock == INVALID_SOCKET)
    {
        fprintf(stderr, "socket falhou: %ld\n", WSAGetLastError());
        return INVALID_SOCKET;
    }

    int opt = 1;
    setsockopt(listenSock, SOL_SOCKET, SO_REUSEADDR, (const char *)&opt, sizeof(opt));

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_ANY);
    addr.sin_port = htons((u_short)port);

    if (bind(listenSock, (struct sockaddr *)&addr, sizeof(addr)) == SOCKET_ERROR)
    {
        fprintf(stderr, "bind falhou: %ld\n", WSAGetLastError());
        closesocket(listenSock);
        return INVALID_SOCKET;
    }

    if (listen(listenSock, SOMAXCONN) == SOCKET_ERROR)
    {
        fprintf(stderr, "listen falhou: %ld\n", WSAGetLastError());
        closesocket(listenSock);
        return INVALID_SOCKET;
    }

    return listenSock;
}

SOCKET net_accept_client(SOCKET serverSock)
{
    struct sockaddr_in clientAddr;
    int addrLen = sizeof(clientAddr);
    SOCKET clientSock = accept(serverSock, (struct sockaddr *)&clientAddr, &addrLen);
    if (clientSock == INVALID_SOCKET)
    {
        fprintf(stderr, "accept falhou: %ld\n", WSAGetLastError());
    }
    return clientSock;
}

int net_set_timeout(SOCKET sock, int seconds)
{
    int timeout = seconds * 1000;
    if (setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, (const char *)&timeout, sizeof(timeout)) == SOCKET_ERROR)
        return 0;
    if (setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, (const char *)&timeout, sizeof(timeout)) == SOCKET_ERROR)
        return 0;
    return 1;
}

int net_send_text(SOCKET sock, const char *text)
{
    if (!text)
        return 0;
    size_t len = strlen(text);
    size_t total = 0;
    while (total < len)
    {
        int sent = send(sock, text + total, (int)(len - total), 0);
        if (sent == SOCKET_ERROR)
            return 0;
        total += sent;
    }
    return 1;
}

int net_send_line(SOCKET sock, const char *text)
{
    if (!net_send_text(sock, text))
        return 0;
    if (!net_send_text(sock, "\n"))
        return 0;
    return 1;
}

int net_recv_line(SOCKET sock, char *buffer, int maxLen)
{
    if (!buffer || maxLen <= 1)
        return -1;

    int received = 0;
    while (received < maxLen - 1)
    {
        char c;
        int res = recv(sock, &c, 1, 0);
        if (res == 0)
            return -1;
        if (res == SOCKET_ERROR)
        {
            int err = WSAGetLastError();
            if (err == WSAEWOULDBLOCK || err == WSAETIMEDOUT)
            {
                if (received > 0)
                    break;
                return 0;
            }
            return -1;
        }
        if (c == '\n')
            break;
        if (c == '\r')
            continue;
        buffer[received++] = c;
    }
    buffer[received] = '\0';
    return received > 0 ? 1 : 0;
}

void net_close_socket(SOCKET sock)
{
    if (sock != INVALID_SOCKET)
    {
        shutdown(sock, SD_BOTH);
        closesocket(sock);
    }
}

