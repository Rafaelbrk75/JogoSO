#include "sharedmem.h"

#include <stdio.h>
#include <string.h>

int sharedmem_init(SharedMemory *mem, int create)
{
    if (!mem)
        return 0;

    memset(mem, 0, sizeof(SharedMemory));

    if (create)
    {
        mem->mapping = CreateFileMappingA(INVALID_HANDLE_VALUE,
                                          NULL,
                                          PAGE_READWRITE,
                                          0,
                                          sizeof(SharedState),
                                          SHARED_MAPPING_NAME);
    }
    else
    {
        mem->mapping = OpenFileMappingA(FILE_MAP_ALL_ACCESS, FALSE, SHARED_MAPPING_NAME);
    }

    if (!mem->mapping)
    {
        fprintf(stderr, "Falha ao criar/abrir mapeamento (%lu)\n", GetLastError());
        return 0;
    }

    mem->state = (SharedState *)MapViewOfFile(mem->mapping,
                                              FILE_MAP_ALL_ACCESS,
                                              0, 0,
                                              sizeof(SharedState));
    if (!mem->state)
    {
        fprintf(stderr, "MapViewOfFile falhou (%lu)\n", GetLastError());
        CloseHandle(mem->mapping);
        mem->mapping = NULL;
        return 0;
    }

    mem->mutex = CreateMutexA(NULL, FALSE, SHARED_MUTEX_NAME);
    if (!mem->mutex)
    {
        fprintf(stderr, "CreateMutex falhou (%lu)\n", GetLastError());
        UnmapViewOfFile(mem->state);
        CloseHandle(mem->mapping);
        mem->mapping = NULL;
        mem->state = NULL;
        return 0;
    }

    if (create)
    {
        DWORD err = GetLastError();
        if (err != ERROR_ALREADY_EXISTS)
        {
            sharedmem_lock(mem);
            memset(mem->state, 0, sizeof(SharedState));
            sharedmem_unlock(mem);
        }
    }

    return 1;
}

void sharedmem_close(SharedMemory *mem)
{
    if (!mem)
        return;
    if (mem->state)
    {
        UnmapViewOfFile(mem->state);
        mem->state = NULL;
    }
    if (mem->mapping)
    {
        CloseHandle(mem->mapping);
        mem->mapping = NULL;
    }
    if (mem->mutex)
    {
        CloseHandle(mem->mutex);
        mem->mutex = NULL;
    }
}

int sharedmem_lock(SharedMemory *mem)
{
    if (!mem || !mem->mutex)
        return 0;
    DWORD res = WaitForSingleObject(mem->mutex, 5000);
    return res == WAIT_OBJECT_0;
}

void sharedmem_unlock(SharedMemory *mem)
{
    if (!mem || !mem->mutex)
        return;
    ReleaseMutex(mem->mutex);
}

void sharedmem_update_turn(SharedMemory *mem, const char *logLine)
{
    if (!mem || !mem->state)
        return;
    if (!sharedmem_lock(mem))
        return;
    mem->state->partidasAtivas = 1;
    if (logLine)
    {
        strncpy(mem->state->ultimoLog, logLine, sizeof(mem->state->ultimoLog) - 1);
        mem->state->ultimoLog[sizeof(mem->state->ultimoLog) - 1] = '\0';
    }
    sharedmem_unlock(mem);
}

void sharedmem_register_result(SharedMemory *mem, PlayerClass winnerClasse)
{
    if (!mem || !mem->state)
        return;
    if (!sharedmem_lock(mem))
        return;
    mem->state->partidasAtivas = 0;
    if (winnerClasse >= CLASS_GUERREIRO && winnerClasse <= CLASS_ARQUEIRO)
    {
        mem->state->vitoriasClasse[winnerClasse]++;
    }
    sharedmem_unlock(mem);
}

