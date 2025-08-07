import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

public class CSVViewer extends JFrame {
    private final List<String> headers = new ArrayList<>();
    private final List<List<Double>> dataColumns = new ArrayList<>();
    private final List<String> timeLabels = new ArrayList<>();

    private final JPanel graphPanel = new JPanel() {
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            drawGraph((Graphics2D) g);
        }
    };

    private final JPanel legendPanel = new JPanel();
    private final JPanel controlPanel = new JPanel();

    private boolean[] visible;
    private int cutoffValue = 100;
    private boolean limitCutoff = true;
    private boolean aggregate = false;
    private int aggregateGroup = 1;

    private final JCheckBox cutoffToggle = new JCheckBox("Limit Recent", true);
    private final JSlider cutoffSlider = new JSlider(10, 1000, 100);
    private final JCheckBox aggregateToggle = new JCheckBox("Aggregate");
    private final JComboBox<Integer> aggregateBox = new JComboBox<>(new Integer[]{1, 2, 5, 10, 20});
    private final JButton saveButton = new JButton("Export PNG");

    private int clickedIndex = -1;

    public CSVViewer() {
        setTitle("CSV Data Analyzer");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLayout(new BorderLayout());
        applyDarkTheme();

        JButton loadButton = new JButton("Load CSV");
        loadButton.addActionListener(e -> loadCSV());

        cutoffSlider.setPaintTicks(true);
        cutoffSlider.setPaintLabels(true);
        cutoffSlider.setMajorTickSpacing(100);
        cutoffSlider.addChangeListener(e -> {
            cutoffValue = cutoffSlider.getValue();
            graphPanel.repaint();
        });

        cutoffToggle.addActionListener(e -> {
            limitCutoff = cutoffToggle.isSelected();
            cutoffSlider.setEnabled(limitCutoff);
            graphPanel.repaint();
        });

        aggregateToggle.addActionListener(e -> {
            aggregate = aggregateToggle.isSelected();
            graphPanel.repaint();
        });

        aggregateBox.addActionListener(e -> {
            aggregateGroup = (Integer) aggregateBox.getSelectedItem();
            graphPanel.repaint();
        });

        saveButton.addActionListener(e -> exportGraph());

        controlPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        controlPanel.add(loadButton);
        controlPanel.add(cutoffToggle);
        controlPanel.add(cutoffSlider);
        controlPanel.add(aggregateToggle);
        controlPanel.add(new JLabel("Group:"));
        controlPanel.add(aggregateBox);
        controlPanel.add(saveButton);
        controlPanel.setBackground(new Color(45, 45, 45));

        legendPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        legendPanel.setBackground(new Color(45, 45, 45));
        legendPanel.setBorder(new EmptyBorder(5, 10, 5, 10));

        graphPanel.setBackground(new Color(30, 30, 30));
        graphPanel.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                clickedIndex = getClosestIndex(e.getX());
                graphPanel.repaint();
            }
        });

        add(controlPanel, BorderLayout.NORTH);
        add(new JScrollPane(graphPanel), BorderLayout.CENTER);
        add(legendPanel, BorderLayout.SOUTH);

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
                            dataColumns.get(i - 1).add(0.0);
                        }
                    }
                }
            }

            visible = new boolean[dataColumns.size()];
            Arrays.fill(visible, true);

            updateLegend();
            cutoffSlider.setMaximum(dataColumns.get(0).size());
            cutoffSlider.setValue(Math.min(dataColumns.get(0).size(), 100));
            graphPanel.repaint();

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to read file.");
        }
    }

    private void updateLegend() {
        legendPanel.removeAll();
        Color[] palette = getColorPalette();

        for (int i = 0; i < headers.size(); i++) {
            JCheckBox cb = new JCheckBox(headers.get(i), true);
            final int index = i;
            cb.addActionListener(e -> {
                visible[index] = cb.isSelected();
                graphPanel.repaint();
            });
            cb.setForeground(palette[i % palette.length]);
            cb.setBackground(new Color(45, 45, 45));
            legendPanel.add(cb);
        }
        legendPanel.revalidate();
        legendPanel.repaint();
    }

    private void drawGraph(Graphics2D g2) {
        if (dataColumns.isEmpty()) return;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = graphPanel.getWidth();
        int h = graphPanel.getHeight();
        int leftMargin = 60;
        int bottomMargin = 40;

        g2.setColor(Color.LIGHT_GRAY);
        g2.drawLine(leftMargin, 10, leftMargin, h - bottomMargin);
        g2.drawLine(leftMargin, h - bottomMargin, w - 10, h - bottomMargin);

        Color[] palette = getColorPalette();
        int totalPoints = dataColumns.get(0).size();
        int limit = limitCutoff ? Math.min(totalPoints, cutoffValue) : totalPoints;

        int groupSize = Math.max(1, aggregate ? aggregateGroup : 1);
        int plotPoints = limit / groupSize;
        int xSpacing = Math.max(1, (w - leftMargin - 10) / Math.max(plotPoints - 1, 1));

        for (int i = 0; i < dataColumns.size(); i++) {
            if (!visible[i]) continue;

            List<Double> col = aggregate(dataColumns.get(i), totalPoints - limit, groupSize, limit);
            double min = col.stream().min(Double::compareTo).orElse(0.0);
            double max = col.stream().max(Double::compareTo).orElse(1.0);
            double range = max - min;

            g2.setColor(palette[i % palette.length]);
            Stroke oldStroke = g2.getStroke();
            g2.setStroke(new BasicStroke(2));

            for (int j = 1; j < col.size(); j++) {
                int x1 = leftMargin + (j - 1) * xSpacing;
                int y1 = (int) (h - bottomMargin - ((col.get(j - 1) - min) / range) * (h - bottomMargin - 10));
                int x2 = leftMargin + j * xSpacing;
                int y2 = (int) (h - bottomMargin - ((col.get(j) - min) / range) * (h - bottomMargin - 10));
                g2.drawLine(x1, y1, x2, y2);
            }
            g2.setStroke(oldStroke);

            double avg = col.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double delta = col.get(col.size() - 1) - col.get(0);
            g2.drawString(String.format("%s: Δ=%.2f | Avg=%.2f", headers.get(i), delta, avg),
                    10, 15 + 15 * i);
        }

        // Draw inspection cursor
        if (clickedIndex != -1) {
            int xi = leftMargin + clickedIndex * xSpacing;
            g2.setColor(Color.YELLOW);
            g2.drawLine(xi, 10, xi, h - bottomMargin);
            g2.drawString("Index: " + clickedIndex, xi + 5, 20);
        }
    }

    private List<Double> aggregate(List<Double> original, int start, int group, int limit) {
        List<Double> result = new ArrayList<>();
        for (int i = start; i < start + limit; i += group) {
            double sum = 0;
            for (int j = 0; j < group && i + j < original.size(); j++)
                sum += original.get(i + j);
            result.add(sum / Math.min(group, original.size() - i));
        }
        return result;
    }

    private int getClosestIndex(int mouseX) {
        int plotWidth = graphPanel.getWidth() - 70;
        int index = (mouseX - 60) * (limitCutoff ? Math.min(cutoffValue, dataColumns.get(0).size()) : dataColumns.get(0).size()) / plotWidth;
        return Math.max(0, index);
    }

    private void exportGraph() {
        BufferedImage image = new BufferedImage(graphPanel.getWidth(), graphPanel.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        graphPanel.paint(g2);
        try {
            JFileChooser fc = new JFileChooser();
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File out = fc.getSelectedFile();
                ImageIO.write(image, "png", out);
                JOptionPane.showMessageDialog(this, "Saved to: " + out.getAbsolutePath());
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to save image.");
        }
    }

    private void applyDarkTheme() {
        UIManager.put("Panel.background", new Color(45, 45, 45));
        UIManager.put("Label.foreground", Color.WHITE);
        UIManager.put("CheckBox.foreground", Color.WHITE);
        UIManager.put("CheckBox.background", new Color(45, 45, 45));
        UIManager.put("ComboBox.background", new Color(60, 60, 60));
        UIManager.put("ComboBox.foreground", Color.WHITE);
        UIManager.put("Button.background", new Color(70, 70, 70));
        UIManager.put("Button.foreground", Color.WHITE);
        UIManager.put("Slider.foreground", Color.WHITE);
        UIManager.put("Slider.background", new Color(45, 45, 45));
    }

    private Color[] getColorPalette() {
        return new Color[]{Color.RED, Color.CYAN, Color.GREEN, Color.ORANGE, Color.MAGENTA, Color.YELLOW, Color.PINK, Color.WHITE};
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(CSVViewer::new);
    }
}
