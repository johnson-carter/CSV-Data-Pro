import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;

public class CSVViewer extends JFrame {
    private List<String> headers = new ArrayList<>();
    private List<List<Double>> dataColumns = new ArrayList<>();
    private List<String> timeLabels = new ArrayList<>();

    private JPanel graphPanel;
    private JPanel checkboxPanel;
    private JSlider cutoffSlider;
    private JCheckBox aggregateToggle;
    private JComboBox aggregateBox;
    private JComboBox<Integer> intervalBox;

    private boolean[] visible;
    private int timeStep = 1;

    public CSVViewer() {
        setTitle("CSV Visualizer");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(900, 600);
        setLayout(new BorderLayout());

        JButton loadButton = new JButton("Load CSV");
        loadButton.addActionListener(e -> loadCSV());

        intervalBox = new JComboBox<>(new Integer[]{1, 2, 5, 10});
        intervalBox.addActionListener(e -> {
            timeStep = (Integer) intervalBox.getSelectedItem();
            graphPanel.repaint();
        });

        JPanel topPanel = new JPanel();
        topPanel.add(loadButton);
        topPanel.add(new JLabel("Time Step:"));
        topPanel.add(intervalBox);
        
        cutoffSlider = new JSlider(10, 100, 100); // placeholder until CSV is loaded
        cutoffSlider.setPaintTicks(true);
        cutoffSlider.setPaintLabels(true);
        cutoffSlider.addChangeListener(e -> graphPanel.repaint());
         
        aggregateToggle = new JCheckBox("Aggregate");         
        aggregateBox = new JComboBox<>(new Integer[]{1, 2, 5, 10, 20});
        aggregateToggle.addActionListener(e -> graphPanel.repaint());
        aggregateBox.addActionListener(e -> graphPanel.repaint());
        
        topPanel.add(new JLabel("Cutoff:"));
        topPanel.add(cutoffSlider);
        topPanel.add(aggregateToggle);
        topPanel.add(new JLabel("Group size:"));
        topPanel.add(aggregateBox);


        checkboxPanel = new JPanel();
        checkboxPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        checkboxPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        graphPanel = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawGraph((Graphics2D) g);
            }
        };
        graphPanel.setBackground(Color.WHITE);

        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(graphPanel), BorderLayout.CENTER);
        add(new JScrollPane(checkboxPanel), BorderLayout.SOUTH);

        setVisible(true);
    }

    private void loadCSV() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        headers.clear();
        dataColumns.clear();
        timeLabels.clear();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            boolean firstLine = true;

            while ((line = br.readLine()) != null) {
                String[] tokens = line.split(",");
                if (firstLine) {
                    headers.addAll(Arrays.asList(tokens).subList(1, tokens.length));
                    for (int i = 1; i < tokens.length; i++)
                        dataColumns.add(new ArrayList<>());
                    firstLine = false;
                } else {
                    timeLabels.add(tokens[0]);
                    for (int i = 1; i < tokens.length; i++) {
                        try {
                            dataColumns.get(i - 1).add(Double.parseDouble(tokens[i]));
                        } catch (NumberFormatException e) {
                            dataColumns.get(i - 1).add(0.0); // fallback
                        }
                    }
                }
            }

            visible = new boolean[dataColumns.size()];
            Arrays.fill(visible, true);

            updateCheckboxPanel();
            showStatistics();
            graphPanel.repaint();

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error reading file.");
        }
    }

    private void updateCheckboxPanel() {
        checkboxPanel.removeAll();
        for (int i = 0; i < headers.size(); i++) {
            int index = i;
            JCheckBox cb = new JCheckBox(headers.get(i), true);
            cb.addItemListener(e -> {
                visible[index] = cb.isSelected();
                graphPanel.repaint();
            });
            checkboxPanel.add(cb);
        }
        checkboxPanel.revalidate();
        checkboxPanel.repaint();
    }

    private void showStatistics() {
        StringBuilder sb = new StringBuilder("Parameter Stats:\n");
        for (int i = 0; i < headers.size(); i++) {
            List<Double> col = dataColumns.get(i);
            if (col.size() < 2) continue;

            double avg = col.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double diff = col.get(col.size() - 1) - col.get(0);

            sb.append(headers.get(i)).append(" → Avg: ")
              .append(String.format("%.2f", avg))
              .append(" | Δ: ")
              .append(String.format("%.2f", diff))
              .append("\n");
        }
        JOptionPane.showMessageDialog(this, sb.toString(), "Statistics", JOptionPane.INFORMATION_MESSAGE);
    }

    private void drawGraph(Graphics2D g2) {
    if (dataColumns.isEmpty()) return;

    int w = graphPanel.getWidth();
    int h = graphPanel.getHeight();
    int leftMargin = 60;
    int bottomMargin = 40;

    g2.setColor(Color.LIGHT_GRAY);
    g2.drawLine(leftMargin, 10, leftMargin, h - bottomMargin);
    g2.drawLine(leftMargin, h - bottomMargin, w - 10, h - bottomMargin);

    Color[] palette = {Color.RED, Color.BLUE, Color.GREEN, Color.MAGENTA, Color.ORANGE, Color.CYAN, Color.BLACK};

    int totalPoints = dataColumns.get(0).size();
    int limit = Math.min(totalPoints, cutoffSlider.getValue());

    int groupSize = (aggregateToggle.isSelected()) ? (Integer) aggregateBox.getSelectedItem() : 1;
    int plotPoints = limit / groupSize;

    int xSpacing = Math.max(1, (w - leftMargin - 10) / Math.max(plotPoints - 1, 1));

    for (int i = 0; i < dataColumns.size(); i++) {
        if (!visible[i]) continue;

        List<Double> original = dataColumns.get(i);
        List<Double> col = new ArrayList<>();

        for (int j = totalPoints - limit; j < totalPoints; j += groupSize) {
            double sum = 0;
            for (int k = 0; k < groupSize && j + k < totalPoints; k++)
                sum += original.get(j + k);
            col.add(sum / Math.min(groupSize, totalPoints - j));
        }

        double minVal = col.stream().min(Double::compareTo).orElse(0.0);
        double maxVal = col.stream().max(Double::compareTo).orElse(1.0);
        double range = maxVal - minVal;

        g2.setColor(palette[i % palette.length]);

        for (int j = 1; j < col.size(); j++) {
            int x1 = leftMargin + (j - 1) * xSpacing;
            int y1 = (int) (h - bottomMargin - ((col.get(j - 1) - minVal) / range) * (h - bottomMargin - 10));
            int x2 = leftMargin + j * xSpacing;
            int y2 = (int) (h - bottomMargin - ((col.get(j) - minVal) / range) * (h - bottomMargin - 10));
            g2.drawLine(x1, y1, x2, y2);
        }
    }
}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(CSVViewer::new);
    }
}
