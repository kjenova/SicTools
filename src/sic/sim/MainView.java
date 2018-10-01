package sic.sim;

import sic.asm.Assembler;
import sic.asm.ErrorCatcher;
import sic.ast.Program;
import sic.common.GUI;
import sic.common.Utils;
import sic.disasm.Disassembler;
import sic.link.ui.LinkListener;
import sic.link.ui.LinkerGui;
import sic.loader.Loader;
import sic.sim.views.DataBreakpointView;
import sic.sim.addons.GraphicalScreen;
import sic.sim.addons.Keyboard;
import sic.sim.addons.TextualScreen;
import sic.sim.views.CPUView;
import sic.sim.views.DisassemblyView;
import sic.sim.views.MemoryView;
import sic.sim.views.WatchView;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.Timer;
import java.util.TimerTask;

/**
 * TODO: write a short description
 *
 * @author jure
 */
public class MainView {
    private final Executor executor;
    private final Disassembler disassembler;

    private JFrame mainFrame;
    // core views
    private CPUView cpuView;
    private DisassemblyView disassemblyView;
    private MemoryView memoryView;
    private WatchView watchView;
    // addon views
    private TextualScreen textScreen;
    private GraphicalScreen graphScreen;
    private Keyboard keyboard;
    private DataBreakpointView dataBreakpointView;

    private File lastLoadedFile;


    public MainView(final Executor executor, Disassembler disassembler) {
        this.executor = executor;
        this.disassembler = disassembler;

        cpuView = new CPUView(executor, disassembler);
        disassemblyView = new DisassemblyView(executor, disassembler);
        memoryView = new MemoryView(executor);
        dataBreakpointView = new DataBreakpointView(executor);
        watchView = new WatchView(executor, actionEvent -> dataBreakpointView.updateView());

        JPanel westPanel = new JPanel(new BorderLayout());
        westPanel.add(cpuView.mainPanel, BorderLayout.NORTH);
        westPanel.add(disassemblyView.mainPanel, BorderLayout.CENTER);

        JPanel eastPanel = new JPanel(new BorderLayout());
        eastPanel.add(watchView.mainPanel, BorderLayout.NORTH);
        eastPanel.add(memoryView.mainPanel, BorderLayout.CENTER);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(westPanel, BorderLayout.WEST);
        mainPanel.add(eastPanel, BorderLayout.CENTER);

        mainFrame = new JFrame("SicTools");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setJMenuBar(createMenuBar());
        mainFrame.setContentPane(mainPanel);
        mainFrame.pack();
        mainFrame.setLocation(0, 0);
        mainFrame.setVisible(true);

        textScreen = new TextualScreen(executor);
        graphScreen = new GraphicalScreen(executor);
        keyboard = new Keyboard(executor);

        Timer timer = new Timer();
        TimerTask timerTask = new TimerTask() {
            public void run() {
                if (mainFrame.isVisible() && executor.hasChanged()) {
                    updateView();
                }
                textScreen.updateView();
                graphScreen.updateView();
            }
        };
        timer.schedule(timerTask, 0, 50);
    }

    public void updateView() {
        cpuView.updateView();
        disassemblyView.updateView(!executor.isRunning(), !executor.isRunning());
        memoryView.updateView();
        watchView.updateView();
    }

    private JMenuBar createMenuBar() {
        JMenuBar mb = new JMenuBar();

        // SicSim
        JMenu menu = new JMenu("SicTools");
        GUI.addMenuItem(menu, "About", KeyEvent.VK_A, actionEvent -> showAboutMessage());
        menu.addSeparator();
        GUI.addMenuItem(menu, "Settings", KeyEvent.VK_S, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                showSettingsView();
            }
        });
        menu.addSeparator();
        GUI.addMenuItem(menu, "Quit", KeyEvent.VK_Q, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                mainFrame.dispatchEvent(new WindowEvent(mainFrame, WindowEvent.WINDOW_CLOSING));
            }
        });
        mb.add(menu);

        // Machine
        menu = new JMenu("Machine");
        GUI.addMenuItem(menu, "Load asm", KeyEvent.VK_A, KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK) , new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                File file = GUI.openFileDialog(mainFrame, new FileNameExtensionFilter("Sic assembler files", "asm"));
                if (file != null) loadAsm(file);
            }
        });
        GUI.addMenuItem(menu, "Load obj", KeyEvent.VK_O, KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK), new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                File file = GUI.openFileDialog(mainFrame, new FileNameExtensionFilter("Sic object files", "obj"));
                if (file != null) loadObj(file);
            }
        });
        GUI.addMenuItem(menu, "Link & load objs", KeyEvent.VK_M, KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK), new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {


                LinkerGui linkerGui = new LinkerGui(null, null, new LinkListener() {
                    @Override
                    public void onLinked(File f, String message) {
                        if (f != null) {
                            loadObj(f);
                            updateView();
                        } else {
                            LinkerGui.showError(message);
                        }
                    }
                });

                linkerGui.gui();

            }
        });
        menu.addSeparator();
        GUI.addMenuItem(menu, "Clear all", KeyEvent.VK_C, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                executor.getMachine().registers.reset();
                executor.getMachine().memory.reset();
                updateView();
            }
        });
        GUI.addMenuItem(menu, "Clear registers", KeyEvent.VK_R, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                executor.getMachine().registers.reset();
                updateView();
            }
        });
        GUI.addMenuItem(menu, "Clear memory", KeyEvent.VK_M, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                executor.getMachine().memory.reset();
                updateView();
            }
        });
        menu.addSeparator();
        GUI.addMenuItem(menu, "Clear & reload", KeyEvent.VK_C, KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK), new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                executor.getMachine().registers.reset();
                executor.getMachine().memory.reset();
                loadLastLoaded();
                updateView();
            }
        });
        mb.add(menu);


        // Debug
        menu = new JMenu("Debug");
        GUI.addMenuItem(menu, "Start", KeyEvent.VK_S, KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                executor.start();
            }
        });
        GUI.addMenuItem(menu, "Step", KeyEvent.VK_T, KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0), new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                executor.step();
            }
        });
        GUI.addMenuItem(menu, "Stop", KeyEvent.VK_P, KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0), new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                executor.stop();
            }
        });
        menu.addSeparator();
        GUI.addMenuItem(menu, "Step out", KeyEvent.VK_O, KeyStroke.getKeyStroke(KeyEvent.VK_F6, InputEvent.SHIFT_DOWN_MASK), new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                executor.stepOut();
            }
        });
        GUI.addMenuItem(menu, "Run to next line", KeyEvent.VK_N, KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0), new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                executor.runToAddress(disassembler.getNextPCLocation());
            }
        });
        GUI.addMenuItem(menu, "Run to cursor", KeyEvent.VK_C, KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0), new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                executor.runToAddress(disassemblyView.getSelectedAddress());
            }
        });
        menu.addSeparator();
        GUI.addMenuItem(menu, "Toggle breakpoint", KeyEvent.VK_B, KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK), new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                disassemblyView.toggleBreakpointAtSelectedRow();
            }
        });
        mb.add(menu);

        // View
        menu = new JMenu("View");
        GUI.addMenuItem(menu, "Textual screen", KeyEvent.VK_T, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                textScreen.toggleView();
            }
        });
        GUI.addMenuItem(menu, "Graphical screen", KeyEvent.VK_G, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                graphScreen.toggleView();
            }
        });
        GUI.addMenuItem(menu, "Keyboard", KeyEvent.VK_K, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                keyboard.toggleView();
            }
        });
        menu.addSeparator();
        GUI.addMenuItem(menu, "Data breakpoints", KeyEvent.VK_D, KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK), new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                dataBreakpointView.toggleView();
            }
        });
        mb.add(menu);

        return mb;
    }

    public void load(File file) {
        String ext = Utils.getFileExtension(file.getName());
        if ("asm".equals(ext))
            loadAsm(file);
        else if ("obj".equals(ext))
            loadObj(file);
        else
            JOptionPane.showMessageDialog(mainFrame, "Invalid filename extension.");
    }

    public void loadObj(File file) {
        disassemblyView.clearLabelMap();
        watchView.clearLabelMap();
        try {
            Reader reader = new FileReader(file);
            Loader.loadSection(executor.machine, reader);
            lastLoadedFile = file;
            mainFrame.setTitle(file.getName());
			updateView();
        } catch (FileNotFoundException e1) {
            JOptionPane.showMessageDialog(mainFrame, "Error loading object file.");
            updateView();
        }
    }

    public void loadAsm(File file) {
        Assembler assembler = new Assembler();
        ErrorCatcher errorCatcher = assembler.errorCatcher;
        Program program = assembler.assemble(Utils.readFile(file));
        if (errorCatcher.count() > 0) {
            errorCatcher.print();
            return;
        }

        Writer writer = new StringWriter();
        assembler.generateObj(program, writer, false);
        Reader reader = new StringReader(writer.toString());
        Loader.loadSection(executor.machine, reader);
        lastLoadedFile = file;
        mainFrame.setTitle(file.getName());

        disassemblyView.setLabelMap(program.getLabels());
        watchView.clearLabelMap();
        watchView.setLabelMap(program.getDataLabels());

        updateView();
    }


    private void loadLastLoaded() {
        if (lastLoadedFile == null) return; // No file loaded before

        load(lastLoadedFile);
    }


    private JPanel createSettingsGeneralPane() {
        JPanel pane = new JPanel();
        pane.setLayout(new GridLayout(1, 2, 0, 0));

        final JTextField txtFreq = GUI.createField(pane, "Frequency", Integer.toString(executor.getSpeed()), 10);
        txtFreq.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                try {
                    executor.setSpeed(Integer.parseInt(txtFreq.getText()));
                } catch (NumberFormatException e) {}
            }
        });
        return pane;
    }

    private void showSettingsView() {
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.addTab("General", null, createSettingsGeneralPane(), null);
        tabs.addTab("Textual screen", null, textScreen.createSettingsPane(), null);
        tabs.addTab("Graphical screen", null, graphScreen.createSettingsPane(), null);
        tabs.addTab("Keyboard", null, keyboard.createSettingsPane(), null);
        GUI.showInJFrame("Settings", tabs, 0, 0);
    }

    private void showAboutMessage() {
        JButton okButton = new JButton("OK");
        JButton licenseButton = new JButton("License");
        okButton.addActionListener(actionEvent -> {
            Window w = SwingUtilities.getWindowAncestor(okButton);
            if (w != null) w.setVisible(false);
        });
        licenseButton.addActionListener(actionEvent -> {
            JTextArea textArea = new JTextArea("Copyright (c) 2015 Jurij Mihelič\nAll rights reserved.\n\nRedistribution and use in source and binary forms, with or without\nmodification, are permitted provided that the following conditions are met:\n\n1. Redistributions of source code must retain the above copyright notice, this\n   list of conditions and the following disclaimer.\n   2. Redistributions in binary form must reproduce the above copyright notice,\n      this list of conditions and the following disclaimer in the documentation\n\t     and/or other materials provided with the distribution.\n\nTHIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS \"AS IS\" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.\n\nhttp://www.opensource.org/licenses/BSD-2-Clause" + "\n\n\n"
                + "Copyright 1997, 1998 Sun Microsystems, Inc.  All Rights Reserved.\n\nRedistribution and use in source and binary forms, with or without\nmodification, are permitted provided that the following conditions\nare met:\n\n  - Redistributions of source code must retain the above copyright\n    notice, this list of conditions and the following disclaimer.\n\n  - Redistributions in binary form must reproduce the above copyright\n    notice, this list of conditions and the following disclaimer in the\n    documentation and/or other materials provided with the distribution.\n\n  - Neither the name of Sun Microsystems nor the names of its\n    contributors may be used to endorse or promote products derived\n    from this software without specific prior written permission.\n\nTHIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS \"AS IS\" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE."
            );
            JScrollPane scrollPane = new JScrollPane(textArea);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            scrollPane.setPreferredSize(new Dimension( 500, 500 ));
            JOptionPane.showMessageDialog(mainFrame, scrollPane, "License", JOptionPane.INFORMATION_MESSAGE);
        });
        Object[] options = { licenseButton, okButton };
        JOptionPane.showOptionDialog(mainFrame, "SicTools: SIC/XE assembler and simulator 2.0\n\n(C) 2011-2018, Jurij Mihelič (jurij.mihelic@fri.uni-lj.si)\n\nContributors\nTomaž Dobravec (tomaz.dobravec@fri.uni-lj.si)\nNejc Kišek\nJakob Erzar\nand others, listed on GitHub", "About", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, okButton);
    }

}
