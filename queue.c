#include "queue.h"

#include <stdio.h>
#include <string.h>
#include <time.h>

void queue_init(MessageQueue *queue)
{
    memset(queue, 0, sizeof(MessageQueue));
    queue->mutex = CreateMutexA(NULL, FALSE, NULL);
}

void queue_destroy(MessageQueue *queue)
{
    if (!queue)
        return;
    if (queue->mutex)
    {
        CloseHandle(queue->mutex);
        queue->mutex = NULL;
    }
}

void queue_clear(MessageQueue *queue)
{
    if (!queue)
        return;
    WaitForSingleObject(queue->mutex, INFINITE);
    queue->head = queue->tail = queue->count = 0;
    ReleaseMutex(queue->mutex);
}

int queue_push(MessageQueue *queue, const char *message)
{
    if (!queue || !message)
        return 0;

    WaitForSingleObject(queue->mutex, INFINITE);
    if (queue->count == QUEUE_MAX_MESSAGES)
    {
        ReleaseMutex(queue->mutex);
        return 0;
    }

    strncpy(queue->messages[queue->tail], message, QUEUE_MESSAGE_SIZE - 1);
    queue->messages[queue->tail][QUEUE_MESSAGE_SIZE - 1] = '\0';
    queue->tail = (queue->tail + 1) % QUEUE_MAX_MESSAGES;
    queue->count++;
    ReleaseMutex(queue->mutex);
    return 1;
}

int queue_pop(MessageQueue *queue, char *out, int outSize, int timeoutSeconds)
{
    if (!queue || !out || outSize <= 0)
        return 0;

    DWORD timeout = timeoutSeconds > 0 ? timeoutSeconds * 1000 : 0;
    DWORD startTime = GetTickCount();

    while (1)
    {
        WaitForSingleObject(queue->mutex, INFINITE);
        
        if (queue->count > 0)
        {
            strncpy(out, queue->messages[queue->head], outSize - 1);
            out[outSize - 1] = '\0';
            queue->head = (queue->head + 1) % QUEUE_MAX_MESSAGES;
            queue->count--;
            ReleaseMutex(queue->mutex);
            return 1;
        }
        
        ReleaseMutex(queue->mutex);

        if (timeoutSeconds <= 0)
            return 0;

        DWORD elapsed = GetTickCount() - startTime;
        if (elapsed >= timeout)
            return 0;

        Sleep(10);
    }
}
