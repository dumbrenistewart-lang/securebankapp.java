package com.securebank.gui;

import com.securebank.model.Account;
import com.securebank.model.AccountInfo;
import com.securebank.model.Transaction;
import com.securebank.pdf.StatementExporter;
import com.securebank.service.BankException;
import com.securebank.service.BankService;
import com.securebank.service.InputValidator;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class DashboardFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    private static final int IDLE_TIMEOUT_MS = 5 * 60 * 1000;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String user;
    private final BankService bank;
    private final Consumer<String> onLogout;
    private final DecimalFormat money = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private final DefaultTableModel accountModel = readOnlyModel("Account No.", "Type", "Balance", "Opened");
    private final DefaultTableModel historyModel = readOnlyModel("Date / time", "Ref", "Type", "Amount", "Balance after");
    private final JTable accountTable = new JTable(accountModel);
    private final JTable historyTable = new JTable(historyModel);
    private final JTextField amountField = new JTextField(10);
    private final JLabel status = new JLabel(" ");
    private List<AccountInfo> accounts = List.of();

    private final javax.swing.Timer idleTimer;
    private final AWTEventListener activityListener;

    public DashboardFrame(String user, BankService bank, Consumer<String> onLogout) {
        super("Secure Banking Application - " + user);
        this.user = user;
        this.bank = bank;
        this.onLogout = onLogout;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JPanel header = new JPanel(new BorderLayout());
        JLabel welcome = new JLabel("Welcome, " + user);
        welcome.setFont(welcome.getFont().deriveFont(Font.BOLD, 16f));
        JButton logout = new JButton("Logout");
        logout.addActionListener(e -> logout(null));
        header.add(welcome, BorderLayout.WEST);
        header.add(logout, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        accountTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        accountTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) refreshHistory();
        });
        accountTable.setRowHeight(22);
        historyTable.setRowHeight(20);
        JPanel top = titled("My accounts", new JScrollPane(accountTable));
        JPanel bottom = titled("Transaction history (selected account)", new JScrollPane(historyTable));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom);
        split.setResizeWeight(0.4);
        add(split, BorderLayout.CENTER);

        JButton deposit = new JButton("Deposit");
        JButton withdraw = new JButton("Withdraw");
        JButton newAccount = new JButton("Open new account");
        JButton export = new JButton("Export statement (PDF)");
        deposit.addActionListener(e -> transact(true));
        withdraw.addActionListener(e -> transact(false));
        newAccount.addActionListener(e -> openAccount());
        export.addActionListener(e -> exportPdf());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        actions.add(new JLabel("Amount:"));
        actions.add(amountField);
        actions.add(deposit);
        actions.add(withdraw);
        actions.add(Box.createHorizontalStrut(16));
        actions.add(newAccount);
        actions.add(export);
        JPanel south = new JPanel(new BorderLayout());
        south.add(actions, BorderLayout.CENTER);
        south.add(status, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

        idleTimer = new javax.swing.Timer(IDLE_TIMEOUT_MS, e -> logout("You were signed out due to inactivity."));
        idleTimer.setRepeats(false);
        activityListener = ev -> idleTimer.restart();
        Toolkit.getDefaultToolkit().addAWTEventListener(activityListener,
                AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
        idleTimer.start();

        refreshAccounts(null);
        setSize(820, 560);
        setLocationRelativeTo(null);
    }

    private void transact(boolean deposit) {
        AccountInfo acc = selectedAccount();
        if (acc == null) { warn("Select an account first."); return; }
        try {
            BigDecimal amount = InputValidator.parseAmount(amountField.getText(), false);
            Transaction tx = deposit ? bank.deposit(user, acc.number(), amount)
                                     : bank.withdraw(user, acc.number(), amount);
            amountField.setText("");
            refreshAccounts(acc.number());
            setStatus((deposit ? "Deposited " : "Withdrew ") + money.format(amount)
                    + " - new balance " + money.format(tx.balanceAfter()) + " (" + tx.id() + ")");
        } catch (BankException ex) {
            warn(ex.getMessage());
        }
    }

    private void openAccount() {
        JComboBox<Account.Type> type = new JComboBox<>(Account.Type.values());
        JTextField initial = new JTextField("0.00", 10);
        JPanel p = new JPanel(new GridLayout(0, 2, 6, 6));
        p.add(new JLabel("Account type:"));   p.add(type);
        p.add(new JLabel("Initial deposit:")); p.add(initial);
        if (JOptionPane.showConfirmDialog(this, p, "Open new account",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        try {
            BigDecimal amount = InputValidator.parseAmount(initial.getText(), true);
            AccountInfo created = bank.openAccount(user, (Account.Type) type.getSelectedItem(), amount);
            refreshAccounts(created.number());
            setStatus("Opened " + created.type() + " account " + created.number());
        } catch (BankException ex) {
            warn(ex.getMessage());
        }
    }

    private void exportPdf() {
        AccountInfo acc = selectedAccount();
        if (acc == null) { warn("Select an account to export."); return; }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save statement as PDF");
        chooser.setFileFilter(new FileNameExtensionFilter("PDF document (*.pdf)", "pdf"));
        chooser.setSelectedFile(new File("Statement-" + acc.number() + ".pdf"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        Path target = chooser.getSelectedFile().toPath();
        if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            target = target.resolveSibling(target.getFileName() + ".pdf");
        }
        if (Files.exists(target) && JOptionPane.showConfirmDialog(this,
                target.getFileName() + " already exists. Overwrite it?", "Confirm overwrite",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            StatementExporter.export(user, acc, bank.historyOf(user, acc.number()), target);
            setStatus("Statement saved to " + target.toAbsolutePath());
            if (JOptionPane.showConfirmDialog(this, "Statement saved.\nOpen it now?", "Export complete",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION
                    && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(target.toFile());
            }
        } catch (BankException ex) {
            warn(ex.getMessage());
        } catch (IOException ex) {
            warn("Could not write the PDF: " + ex.getMessage());
        }
    }

    private void logout(String notice) {
        idleTimer.stop();
        Toolkit.getDefaultToolkit().removeAWTEventListener(activityListener);
        dispose();
        onLogout.accept(notice);
    }

    private void refreshAccounts(String keepNumber) {
        accounts = bank.accountsOf(user);
        accountModel.setRowCount(0);
        int select = accounts.isEmpty() ? -1 : 0;
        for (int i = 0; i < accounts.size(); i++) {
            AccountInfo a = accounts.get(i);
            accountModel.addRow(new Object[]{a.number(), a.type(), money.format(a.balance()), a.createdAt().format(STAMP)});
            if (a.number().equals(keepNumber)) select = i;
        }
        if (select >= 0) accountTable.setRowSelectionInterval(select, select);
        refreshHistory();
    }

    private void refreshHistory() {
        historyModel.setRowCount(0);
        AccountInfo acc = selectedAccount();
        if (acc == null) return;
        try {
            for (Transaction t : bank.historyOf(user, acc.number())) {
                String sign = t.type() == Transaction.Type.WITHDRAWAL ? "-" : "+";
                historyModel.addRow(new Object[]{t.timestamp().format(STAMP), t.id(), t.type(),
                        sign + money.format(t.amount()), money.format(t.balanceAfter())});
            }
        } catch (BankException ex) {
            warn(ex.getMessage());
        }
    }

    private AccountInfo selectedAccount() {
        int row = accountTable.getSelectedRow();
        return row < 0 ? null : accounts.get(accountTable.convertRowIndexToModel(row));
    }

    private void setStatus(String text) { status.setText(text); }

    private void warn(String text) {
        JOptionPane.showMessageDialog(this, text, "Secure Banking Application", JOptionPane.WARNING_MESSAGE);
    }

    private static JPanel titled(String title, JComponent inner) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(inner, BorderLayout.CENTER);
        return p;
    }

    private static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
    }
}
