package dev.agnor.network;

import static dev.agnor.network.NpcWire.*;

/** Short player-facing feedback shared by modded and vanilla command clients. */
public final class NpcFeedback {
    private NpcFeedback() {}
    public static String message(Snapshot snapshot) {
        if (snapshot.status() != OK) return switch (snapshot.status()) {
            case VERSION_MISMATCH -> "AGNOR: версия клиента не поддерживается.";
            case INVALID -> "AGNOR: некорректный запрос.";
            case WORLD_MISMATCH -> "AGNOR: запрос относится к другому миру.";
            case DENIED -> "AGNOR: недостаточно прав или NPC слишком далеко.";
            case BUSY -> "AGNOR: подождите перед следующим запросом.";
            case DELETED -> "AGNOR: NPC удалена.";
            default -> "AGNOR: NPC недоступна; повторите позже.";
        };
        if (snapshot.npc().equals(NIL)) return "Операция AGNOR выполнена.";
        return "NPC " + snapshot.npc() + " • " + snapshot.definition() + " • отношение: " + snapshot.affinity()
                + (snapshot.owner() ? " • владелец: вы" : "");
    }
}
