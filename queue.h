#ifndef QUEUE_H
#define QUEUE_H

#include <windows.h>

#define QUEUE_MAX_MESSAGES 64
#define QUEUE_MESSAGE_SIZE 256

typedef struct
{
    char messages[QUEUE_MAX_MESSAGES][QUEUE_MESSAGE_SIZE];
    int head;
    int tail;
    int count;
    HANDLE mutex;
} MessageQueue;

void queue_init(MessageQueue *queue);
void queue_destroy(MessageQueue *queue);
int queue_push(MessageQueue *queue, const char *message);
int queue_pop(MessageQueue *queue, char *out, int outSize, int timeoutSeconds);
void queue_clear(MessageQueue *queue);

#endif /* QUEUE_H */
