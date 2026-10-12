package com.securebank.gui;

import com.securebank.service.AuthService;
import com.securebank.service.BankException;
import com.securebank.service.BankService;

import javax.swing.*;
import java.awt.*;

public final class LoginFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    private final AuthService auth;
    private final BankService bank;
    private final JTextField userField = new JTextField(18);
    private final JPasswordField passField = new JPasswordField(18);
    private final JLabel message = new JLabel(" ", SwingConstants.CENTER);

    public LoginFrame(AuthService auth, BankService bank, String notice) {
        super("Secure Banking Application - Login");
        this.auth = auth;
        this.bank = bank;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(20, 28, 20, 28));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("Secure Banking Application", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
        form.add(title, c);

        c.gridwidth = 1; c.gridy = 1; form.add(new JLabel("Username:"), c);
        c.gridx = 1; form.add(userField, c);
        c.gridx = 0; c.gridy = 2; form.add(new JLabel("Password:"), c);
        c.gridx = 1; form.add(passField, c);

        JButton login = new JButton("Login");
        JButton register = new JButton("Register");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttons.add(login);
        buttons.add(register);
        c.gridx = 0; c.gridy = 3; c.gridwidth = 2; form.add(buttons, c);

        message.setForeground(new Color(180, 30, 30));
        if (notice != null) message.setText(notice);
        c.gridy = 4; form.add(message, c);

        login.addActionListener(e -> doLogin());
        register.addActionListener(e -> doRegister());
        getRootPane().setDefaultButton(login);

        setContentPane(form);
        pack();
        setResizable(false);
        setLocationRelativeTo(null);
    }

    private void doLogin() {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            String user = auth.login(userField.getText(), passField.getPassword());
            DashboardFrame dash = new DashboardFrame(user, bank,
                    notice -> new LoginFrame(auth, bank, notice).setVisible(true));
            dash.setVisible(true);
            dispose();
        } catch (BankException ex) {
            message.setText(ex.getMessage());
        } finally {
            passField.setText("");
            setCursor(Cursor.getDefaultCursor());
        }
    }

    private void doRegister() {
        JTextField u = new JTextField(16);
        JPasswordField p1 = new JPasswordField(16);
        JPasswordField p2 = new JPasswordField(16);
        u.setText(userField.getText());

        while (true) {
            JPanel panel = new JPanel(new GridLayout(0, 2, 6, 6));
            panel.add(new JLabel("Username:"));         panel.add(u);
            panel.add(new JLabel("Password:"));         panel.add(p1);
            panel.add(new JLabel("Confirm password:")); panel.add(p2);
            JLabel hint = new JLabel("<html><small>Password: 8-64 chars, upper + lower case, digit and symbol.</small></html>");
            JPanel wrapper = new JPanel(new BorderLayout(0, 8));
            wrapper.add(panel, BorderLayout.CENTER);
            wrapper.add(hint, BorderLayout.SOUTH);

            int choice = JOptionPane.showConfirmDialog(this, wrapper, "Register new user",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) break;
            try {
                auth.register(u.getText(), p1.getPassword(), p2.getPassword());
                userField.setText(u.getText().trim());
                message.setText(" ");
                JOptionPane.showMessageDialog(this, "Registration successful. You can now log in.");
                break;
            } catch (BankException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Registration failed", JOptionPane.WARNING_MESSAGE);
            } finally {
                p1.setText("");
                p2.setText("");
            }
        }
    }
}
