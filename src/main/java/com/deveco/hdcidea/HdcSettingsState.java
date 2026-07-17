package com.deveco.hdcidea;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Persistent plugin settings — stores the configured path to the hdc executable
 * and the last-used bundle name (as a convenience fallback).
 */
@State(
        name = "HdcSettingsState",
        storages = @Storage("hdc-idea.xml")
)
public class HdcSettingsState implements PersistentStateComponent<HdcSettingsState> {

    /** User-configured absolute path to hdc executable. Empty = auto-detect. */
    public String hdcPath = "";

    /** Last-used bundle name, remembered across actions. */
    public String bundleName = "";

    /** Last-used ability name, remembered across start/restart actions. */
    public String abilityName = "";

    public static HdcSettingsState getInstance() {
        return ApplicationManager.getApplication().getService(HdcSettingsState.class);
    }

    @Nullable
    @Override
    public HdcSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull HdcSettingsState state) {
        this.hdcPath = state.hdcPath;
        this.bundleName = state.bundleName;
        this.abilityName = state.abilityName;
    }
}
