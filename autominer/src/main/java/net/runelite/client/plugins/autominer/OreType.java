package net.runelite.client.plugins.autominer;

public enum OreType {
    ADAMANTITE("Adamantite"),
    COAL("Coal"),
    RUNITE("Runite"),
    GEM_ROCK("Gem rock");

    private final String displayName;

    OreType(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
