package common;

public class SharedState {
    private int partidasAtivas;
    private final int[] vitoriasClasse = new int[3];
    private String ultimoLog = "";

    public int getPartidasAtivas() {
        return partidasAtivas;
    }

    public void setPartidasAtivas(int partidasAtivas) {
        this.partidasAtivas = partidasAtivas;
    }

    public int[] getVitoriasClasse() {
        return vitoriasClasse;
    }

    public void setVitoriaCount(int index, int value) {
        if (index >= 0 && index < vitoriasClasse.length) {
            vitoriasClasse[index] = value;
        }
    }

    public void incrementVictory(PlayerClass classe) {
        switch (classe) {
            case GUERREIRO:
                vitoriasClasse[0]++;
                break;
            case MAGO:
                vitoriasClasse[1]++;
                break;
            case ARQUEIRO:
                vitoriasClasse[2]++;
                break;
            default:
                break;
        }
    }

    public String getUltimoLog() {
        return ultimoLog;
    }

    public void setUltimoLog(String ultimoLog) {
        this.ultimoLog = ultimoLog;
    }
}

