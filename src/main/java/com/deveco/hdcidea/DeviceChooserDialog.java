package com.deveco.hdcidea;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Dialog for selecting a target device when multiple devices are connected.
 */
public class DeviceChooserDialog extends DialogWrapper {

    private final JBList<String> deviceList;

    public DeviceChooserDialog(List<String> devices) {
        super(true); // use current window as parent
        setTitle("选择设备");
        setResizable(false);

        deviceList = new JBList<>(devices.toArray(new String[0]));
        deviceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        if (!devices.isEmpty()) {
            deviceList.setSelectedIndex(0);
        }
        // 双击直接选中并确认
        deviceList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    doOKAction();
                }
            }
        });

        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JBScrollPane scrollPane = new JBScrollPane(deviceList);
        scrollPane.setPreferredSize(new Dimension(400, 200));
        return scrollPane;
    }

    @Nullable
    @Override
    protected JComponent createNorthPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(new JLabel("请选择要执行 HDC 命令的目标设备："));
        return panel;
    }

    @Override
    protected void createDefaultActions() {
        super.createDefaultActions();
        // 把 DialogWrapper 默认的 OK / Cancel 按钮文字改为中文
        myOKAction.putValue(Action.NAME, "确定");
        myCancelAction.putValue(Action.NAME, "取消");
    }

    /**
     * @return the selected device serial, or null if none selected
     */
    @Nullable
    public String getSelectedDevice() {
        return deviceList.getSelectedValue();
    }
}
