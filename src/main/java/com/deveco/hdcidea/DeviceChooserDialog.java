package com.deveco.hdcidea;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Dialog for selecting a target device when multiple devices are connected.
 */
public class DeviceChooserDialog extends DialogWrapper {

    private final JBList<String> deviceList;

    public DeviceChooserDialog(List<String> devices) {
        super(true); // use current window as parent
        setTitle("Select Device");
        setResizable(false);

        deviceList = new JBList<>(devices.toArray(new String[0]));
        deviceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        if (!devices.isEmpty()) {
            deviceList.setSelectedIndex(0);
        }

        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JBScrollPane scrollPane = new JBScrollPane(deviceList);
        scrollPane.setPreferredSize(new Dimension(400, 200));
        return scrollPane;
    }

    @Override
    protected JComponent createSouthPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(new JLabel("Select a target device for the HDC command."));
        return panel;
    }

    /**
     * @return the selected device serial, or null if none selected
     */
    @Nullable
    public String getSelectedDevice() {
        return deviceList.getSelectedValue();
    }
}
