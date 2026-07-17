package com.deveco.hdcidea;

import com.intellij.openapi.options.Configurable;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Settings page under Settings > Tools > HDC Idea.
 * Allows the user to configure the path to the hdc executable
 * and optionally override the auto-detected bundle name.
 */
public class HdcPathConfigurable implements Configurable {

    private JPanel mainPanel;
    private JTextField hdcPathField;
    private JTextField bundleNameField;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "HDC Idea";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        hdcPathField = new JTextField();
        bundleNameField = new JTextField();

        mainPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent("hdc executable path:", hdcPathField)
                .addTooltip("Leave empty to auto-detect from DevEco Studio SDK or system PATH.")
                .addSeparator()
                .addLabeledComponent("Bundle name override:", bundleNameField)
                .addTooltip("Optional. If set, this bundle name is used instead of auto-detection.")
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();

        return mainPanel;
    }

    @Override
    public boolean isModified() {
        HdcSettingsState s = HdcSettingsState.getInstance();
        return !hdcPathField.getText().equals(s.hdcPath)
                || !bundleNameField.getText().equals(s.bundleName);
    }

    @Override
    public void apply() {
        HdcSettingsState s = HdcSettingsState.getInstance();
        s.hdcPath = hdcPathField.getText();
        s.bundleName = bundleNameField.getText();
    }

    @Override
    public void reset() {
        HdcSettingsState s = HdcSettingsState.getInstance();
        hdcPathField.setText(s.hdcPath);
        bundleNameField.setText(s.bundleName);
    }

    @Override
    public void disposeUIResources() {
        mainPanel = null;
        hdcPathField = null;
        bundleNameField = null;
    }
}
