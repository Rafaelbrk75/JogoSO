#ifndef SHAREDMEM_H
#define SHAREDMEM_H

#include <windows.h>

#include "protocol.h"

#define SHARED_MAPPING_NAME "DUELRPG_MM"
#define SHARED_MUTEX_NAME "DUELRPG_MM_MUTEX"

typedef struct
{
    int partidasAtivas;
    int vitoriasClasse[3];
    char ultimoLog[MAX_LOG_SIZE];
} SharedState;

typedef struct
{
    HANDLE mapping;
    HANDLE mutex;
    SharedState *state;
} SharedMemory;

int sharedmem_init(SharedMemory *mem, int create);
void sharedmem_close(SharedMemory *mem);
int sharedmem_lock(SharedMemory *mem);
void sharedmem_unlock(SharedMemory *mem);
void sharedmem_update_turn(SharedMemory *mem, const char *logLine);
void sharedmem_register_result(SharedMemory *mem, PlayerClass winnerClasse);

#endif /* SHAREDMEM_H */

