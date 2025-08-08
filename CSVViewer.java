import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Path2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;

public class CSVViewer extends JFrame {
    private Map<String, List<Double>> data;
    private List<String> columnNames;
    private ChartPanel chartPanel;
    private JPanel controlPanel;
    private Map<String, JSlider> scaleSliders;
    private Map<String, JCheckBox> variableCheckboxes;
    private Map<String, JCheckBox> derivativeCheckboxes;
    private Map<String, Color> variableColors;
    private JLabel statusLabel;
    private JLabel mousePositionLabel;
    
    // Enhanced color palette with better visibility
    private static final Color[] COLORS = {
        new Color(31, 119, 180),   // Blue
        new Color(255, 127, 14),   // Orange  
        new Color(44, 160, 44),    // Green
        new Color(214, 39, 40),    // Red
        new Color(148, 103, 189),  // Purple
        new Color(140, 86, 75),    // Brown
        new Color(227, 119, 194),  // Pink
        new Color(127, 127, 127),  // Gray
        new Color(188, 189, 34),   // Olive
        new Color(23, 190, 207)    // Cyan
    };
    
    // Chart display options
    private boolean showDataPoints = false;
    private boolean showZeroLine = true;
    private boolean enableSmoothing = false;
    
    // Data aggregation options
    private int globalAggregationWindow = 1; // 1 means no aggregation
    private Map<String, Integer> variableAggregationWindows;
    
    public CSVViewer() {
        data = new HashMap<>();
        columnNames = new ArrayList<>();
        scaleSliders = new HashMap<>();
        variableCheckboxes = new HashMap<>();
        derivativeCheckboxes = new HashMap<>();
        variableColors = new HashMap<>();
        variableAggregationWindows = new HashMap<>();
        
        initializeUI();
    }
    
    private void initializeUI() {
        setTitle("CSV Data Visualization Dashboard");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        
        // Create menu bar
        createMenuBar();
        
        // Create chart panel with mouse interaction
        chartPanel = new ChartPanel();
        chartPanel.setPreferredSize(new Dimension(800, 600));
        chartPanel.setBackground(Color.WHITE);
        add(chartPanel, BorderLayout.CENTER);
        
        // Create control panel
        createControlPanel();
        
        // Create status bar
        createStatusBar();
        
        pack();
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
    }
    
    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        
        // File menu
        JMenu fileMenu = new JMenu("File");
        JMenuItem loadItem = new JMenuItem("Load CSV");
        loadItem.setAccelerator(KeyStroke.getKeyStroke("ctrl O"));
        loadItem.addActionListener(e -> loadCSV());
        
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.setAccelerator(KeyStroke.getKeyStroke("ctrl Q"));
        exitItem.addActionListener(e -> System.exit(0));
        
        fileMenu.add(loadItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        
        // View menu
        JMenu viewMenu = new JMenu("View");
        JMenuItem resetScalesItem = new JMenuItem("Reset All Scales");
        resetScalesItem.addActionListener(e -> resetAllScales());
        
        JMenuItem showAllItem = new JMenuItem("Show All Variables");
        showAllItem.addActionListener(e -> showAllVariables());
        
        JMenuItem hideAllItem = new JMenuItem("Hide All Variables");
        hideAllItem.addActionListener(e -> hideAllVariables());
        
        JCheckBoxMenuItem showPointsItem = new JCheckBoxMenuItem("Show Data Points", showDataPoints);
        showPointsItem.addActionListener(e -> {
            showDataPoints = showPointsItem.isSelected();
            chartPanel.repaint();
        });
        
        JCheckBoxMenuItem showZeroItem = new JCheckBoxMenuItem("Show Zero Line", showZeroLine);
        showZeroItem.addActionListener(e -> {
            showZeroLine = showZeroItem.isSelected();
            chartPanel.repaint();
        });
        
        JCheckBoxMenuItem smoothingItem = new JCheckBoxMenuItem("Enable Smoothing", enableSmoothing);
        smoothingItem.addActionListener(e -> {
            enableSmoothing = smoothingItem.isSelected();
            chartPanel.repaint();
        });
        
        // Data aggregation submenu
        JMenu aggregationMenu = new JMenu("Data Aggregation");
        ButtonGroup aggGroup = new ButtonGroup();
        
        int[] windowSizes = {1, 2, 5, 10, 25};
        String[] windowLabels = {"None (1)", "2 Points", "5 Points", "10 Points", "25 Points"};
        
        for (int i = 0; i < windowSizes.length; i++) {
            final int windowSize = windowSizes[i];
            JRadioButtonMenuItem aggItem = new JRadioButtonMenuItem(windowLabels[i], windowSize == 1);
            aggItem.addActionListener(e -> {
                globalAggregationWindow = windowSize;
                // Reset all variable-specific aggregation to use global
                variableAggregationWindows.clear();
                for (String columnName : columnNames) {
                    variableAggregationWindows.put(columnName, windowSize);
                }
                chartPanel.repaint();
                // Update control panel to reflect changes
                if (!data.isEmpty()) {
                    createControls();
                }
            });
            aggGroup.add(aggItem);
            aggregationMenu.add(aggItem);
        }
        
        viewMenu.add(resetScalesItem);
        viewMenu.addSeparator();
        viewMenu.add(showAllItem);
        viewMenu.add(hideAllItem);
        viewMenu.addSeparator();
        viewMenu.add(showPointsItem);
        viewMenu.add(showZeroItem);
        viewMenu.add(smoothingItem);
        viewMenu.addSeparator();
        viewMenu.add(aggregationMenu);
        
        menuBar.add(fileMenu);
        menuBar.add(viewMenu);
        setJMenuBar(menuBar);
    }
    
    private void createControlPanel() {
        controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        controlPanel.setBackground(new Color(245, 245, 245));
        
        JScrollPane controlScrollPane = new JScrollPane(controlPanel);
        controlScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        controlScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        controlScrollPane.setPreferredSize(new Dimension(300, 600));
        controlScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        controlScrollPane.getVerticalScrollBar().setBlockIncrement(48);
        
        // Ensure the scroll pane respects the panel's preferred size
        controlPanel.setAutoscrolls(true);
        
        add(controlScrollPane, BorderLayout.EAST);
    }
    
    private void createStatusBar() {
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createLoweredBevelBorder());
        
        statusLabel = new JLabel("Ready - Load a CSV file to begin");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        
        mousePositionLabel = new JLabel("Mouse: ");
        mousePositionLabel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        
        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(mousePositionLabel, BorderLayout.EAST);
        
        add(statusPanel, BorderLayout.SOUTH);
    }
    
    private void loadCSV() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setFileFilter(new FileNameExtensionFilter("CSV Files", "csv"));
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            statusLabel.setText("Loading: " + selectedFile.getName());
            
            SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    parseCSV(selectedFile);
                    return null;
                }
                
                @Override
                protected void done() {
                    try {
                        get(); // Check for exceptions
                        createControls();
                        chartPanel.repaint();
                        statusLabel.setText("Loaded: " + selectedFile.getName() + 
                            " (" + columnNames.size() + " variables, " + 
                            getMaxDataPoints() + " points)");
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(CSVViewer.this, 
                            "Error loading CSV: " + e.getMessage(), 
                            "Error", JOptionPane.ERROR_MESSAGE);
                        statusLabel.setText("Error loading file");
                    }
                }
            };
            worker.execute();
        }
    }
    
    private void parseCSV(File file) throws IOException {
        data.clear();
        columnNames.clear();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();
            if (line == null) {
                throw new IOException("File is empty");
            }
            
            // Parse header - handle quoted fields and different separators
            String[] headers = parseCSVLine(line);
            for (String header : headers) {
                String trimmedHeader = header.trim().replaceAll("^\"|\"$", ""); // Remove quotes
                if (!trimmedHeader.isEmpty()) {
                    columnNames.add(trimmedHeader);
                    data.put(trimmedHeader, new ArrayList<>());
                }
            }
            
            if (columnNames.isEmpty()) {
                throw new IOException("No valid column headers found");
            }
            
            // Parse data rows
            int rowCount = 0;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue; // Skip empty lines
                
                String[] values = parseCSVLine(line);
                rowCount++;
                
                for (int i = 0; i < values.length && i < columnNames.size(); i++) {
                    String value = values[i].trim().replaceAll("^\"|\"$", "");
                    if (!value.isEmpty()) {
                        try {
                            double numericValue = Double.parseDouble(value);
                            data.get(columnNames.get(i)).add(numericValue);
                        } catch (NumberFormatException e) {
                            // Skip non-numeric values silently
                        }
                    }
                }
            }
            
            // Remove columns with no numeric data
            columnNames.removeIf(columnName -> data.get(columnName).isEmpty());
            data.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            
            if (columnNames.isEmpty()) {
                throw new IOException("No numeric data found in CSV");
            }
        }
        
        // Assign colors to variables and initialize aggregation windows
        for (int i = 0; i < columnNames.size(); i++) {
            String columnName = columnNames.get(i);
            variableColors.put(columnName, COLORS[i % COLORS.length]);
            variableAggregationWindows.put(columnName, globalAggregationWindow);
        }
    }
    
    // Enhanced CSV parsing to handle quotes and different separators
    private String[] parseCSVLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(currentField.toString());
                currentField.setLength(0);
            } else {
                currentField.append(c);
            }
        }
        fields.add(currentField.toString());
        
        return fields.toArray(new String[0]);
    }
    
    private int getMaxDataPoints() {
        return data.values().stream().mapToInt(List::size).max().orElse(0);
    }
    
    private void createControls() {
        controlPanel.removeAll();
        scaleSliders.clear();
        variableCheckboxes.clear();
        derivativeCheckboxes.clear();
        
        JLabel titleLabel = new JLabel("Chart Controls");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        controlPanel.add(titleLabel);
        
        // Add global controls
        JPanel globalPanel = new JPanel();
        globalPanel.setLayout(new BoxLayout(globalPanel, BoxLayout.Y_AXIS));
        globalPanel.setBorder(BorderFactory.createTitledBorder("Global Controls"));
        
        JButton resetButton = new JButton("Reset All Scales");
        resetButton.addActionListener(e -> resetAllScales());
        
        JButton exportButton = new JButton("Export Data");
        exportButton.addActionListener(e -> exportVisibleData());
        
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(resetButton);
        buttonPanel.add(exportButton);
        
        // Global aggregation control
        JPanel aggPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        aggPanel.add(new JLabel("Global Aggregation:"));
        
        String[] aggOptions = {"None (1)", "2 Points", "5 Points", "10 Points", "25 Points"};
        int[] aggValues = {1, 2, 5, 10, 25};
        
        JComboBox<String> globalAggCombo = new JComboBox<>(aggOptions);
        for (int i = 0; i < aggValues.length; i++) {
            if (aggValues[i] == globalAggregationWindow) {
                globalAggCombo.setSelectedIndex(i);
                break;
            }
        }
        
        globalAggCombo.addActionListener(e -> {
            int selectedIndex = globalAggCombo.getSelectedIndex();
            globalAggregationWindow = aggValues[selectedIndex];
            // Update all variables to use the new global setting
            for (String columnName : columnNames) {
                variableAggregationWindows.put(columnName, globalAggregationWindow);
            }
            chartPanel.repaint();
            // Recreate controls to update individual combo boxes
            createControls();
        });
        
        aggPanel.add(globalAggCombo);
        
        globalPanel.add(buttonPanel);
        globalPanel.add(aggPanel);
        
        controlPanel.add(globalPanel);
        controlPanel.add(Box.createVerticalStrut(10));
        
        // Create controls for each variable
        for (String columnName : columnNames) {
            if (data.get(columnName).isEmpty()) continue;
            
            createVariableControls(columnName);
        }
        
        controlPanel.revalidate();
        controlPanel.repaint();
    }
    
    private void createVariableControls(String columnName) {
        JPanel variablePanel = new JPanel();
        variablePanel.setLayout(new BoxLayout(variablePanel, BoxLayout.Y_AXIS));
        variablePanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), columnName, 
            TitledBorder.LEFT, TitledBorder.TOP));
        variablePanel.setBackground(Color.WHITE);
        variablePanel.setMaximumSize(new Dimension(280, Integer.MAX_VALUE));
        
        // Color indicator and statistics
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        JPanel colorPanel = new JPanel();
        colorPanel.setBackground(variableColors.get(columnName));
        colorPanel.setPreferredSize(new Dimension(20, 20));
        colorPanel.setMaximumSize(new Dimension(20, 20));
        colorPanel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        
        // Add statistics with aggregation info
        List<Double> values = data.get(columnName);
        List<Double> aggregatedValues = aggregateData(values, variableAggregationWindows.get(columnName));
        
        double min = Collections.min(values);
        double max = Collections.max(values);
        double avg = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        DecimalFormat df = new DecimalFormat("#0.##");
        String statsText = String.format("<html>Original: %d points<br/>Aggregated: %d points<br/>Min: %s, Max: %s, Avg: %s</html>", 
            values.size(), aggregatedValues.size(), df.format(min), df.format(max), df.format(avg));
        
        JLabel statsLabel = new JLabel(statsText);
        statsLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        
        headerPanel.add(colorPanel);
        headerPanel.add(new JLabel(" " + columnName));
        
        // Visibility checkbox
        JCheckBox visibilityCheckbox = new JCheckBox("Show Variable", true);
        visibilityCheckbox.addActionListener(e -> chartPanel.repaint());
        variableCheckboxes.put(columnName, visibilityCheckbox);
        
        // Derivative checkbox with options
        JPanel derivativePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JCheckBox derivativeCheckbox = new JCheckBox("Show Derivative", false);
        derivativeCheckbox.addActionListener(e -> chartPanel.repaint());
        derivativeCheckboxes.put(columnName, derivativeCheckbox);
        
        JButton analyzeButton = new JButton("Analyze");
        analyzeButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        analyzeButton.addActionListener(e -> showVariableAnalysis(columnName));
        
        derivativePanel.add(derivativeCheckbox);
        derivativePanel.add(analyzeButton);
        
        // Scale slider with improved range
        JSlider scaleSlider = new JSlider(10, 1000, 100);
        scaleSlider.setMajorTickSpacing(100);
        scaleSlider.setPaintTicks(true);
        scaleSlider.addChangeListener(e -> chartPanel.repaint());
        scaleSliders.put(columnName, scaleSlider);
        
        JLabel scaleLabel = new JLabel("Scale: 100%");
        scaleSlider.addChangeListener(e -> {
            int value = scaleSlider.getValue();
            scaleLabel.setText("Scale: " + value + "%");
        });
        
        // Individual aggregation control
        JPanel individualAggPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        individualAggPanel.add(new JLabel("Aggregation:"));
        
        String[] aggOptions = {"None (1)", "2 Points", "5 Points", "10 Points", "25 Points"};
        int[] aggValues = {1, 2, 5, 10, 25};
        
        JComboBox<String> aggCombo = new JComboBox<>(aggOptions);
        int currentWindow = variableAggregationWindows.getOrDefault(columnName, 1);
        for (int i = 0; i < aggValues.length; i++) {
            if (aggValues[i] == currentWindow) {
                aggCombo.setSelectedIndex(i);
                break;
            }
        }
        
        aggCombo.addActionListener(e -> {
            int selectedIndex = aggCombo.getSelectedIndex();
            variableAggregationWindows.put(columnName, aggValues[selectedIndex]);
            chartPanel.repaint();
            // Update statistics display
            createVariableControls(columnName);
        });
        
        individualAggPanel.add(aggCombo);
        
        // Add components
        variablePanel.add(headerPanel);
        variablePanel.add(statsLabel);
        variablePanel.add(visibilityCheckbox);
        variablePanel.add(derivativePanel);
        variablePanel.add(individualAggPanel);
        variablePanel.add(new JLabel("Vertical Scale:"));
        variablePanel.add(scaleSlider);
        variablePanel.add(scaleLabel);
        
        controlPanel.add(variablePanel);
        controlPanel.add(Box.createVerticalStrut(5));
        
        // Ensure the control panel updates its preferred size
        controlPanel.revalidate();
    }
    
    private List<Double> calculateDerivative(List<Double> values) {
        List<Double> derivative = new ArrayList<>();
        if (values.size() < 2) {
            return derivative;
        }
        
        // Use improved numerical differentiation
        for (int i = 0; i < values.size(); i++) {
            if (i == 0) {
                // Forward difference for first point
                derivative.add(values.get(1) - values.get(0));
            } else if (i == values.size() - 1) {
                // Backward difference for last point
                derivative.add(values.get(i) - values.get(i - 1));
            } else {
                // Central difference for middle points (more accurate)
                derivative.add((values.get(i + 1) - values.get(i - 1)) / 2.0);
            }
        }
        
        return derivative;
    }
    
    private List<Double> aggregateData(List<Double> data, int windowSize) {
        if (windowSize <= 1 || data.isEmpty()) {
            return new ArrayList<>(data);
        }
        
        List<Double> aggregated = new ArrayList<>();
        
        for (int i = 0; i < data.size(); i += windowSize) {
            double sum = 0;
            int count = 0;
            
            // Average the points in the current window
            for (int j = i; j < Math.min(i + windowSize, data.size()); j++) {
                sum += data.get(j);
                count++;
            }
            
            if (count > 0) {
                aggregated.add(sum / count);
            }
        }
        
        return aggregated;
    }
    
    private void resetAllScales() {
        scaleSliders.values().forEach(slider -> slider.setValue(100));
        chartPanel.repaint();
    }
    
    private void showAllVariables() {
        variableCheckboxes.values().forEach(checkbox -> checkbox.setSelected(true));
        chartPanel.repaint();
    }
    
    private void hideAllVariables() {
        variableCheckboxes.values().forEach(checkbox -> checkbox.setSelected(false));
        chartPanel.repaint();
    }
    
    private void exportVisibleData() {
        if (data.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No data to export", "Export", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("CSV Files", "csv"));
        fileChooser.setSelectedFile(new File("exported_data.csv"));
        
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".csv")) {
                file = new File(file.getAbsolutePath() + ".csv");
            }
            
            try {
                exportDataToFile(file);
                statusLabel.setText("Data exported to: " + file.getName());
                JOptionPane.showMessageDialog(this, "Data exported successfully!", "Export", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error exporting data: " + e.getMessage(), 
                    "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void exportDataToFile(File file) throws IOException {
        List<String> visibleColumns = getVisibleColumnNames();
        if (visibleColumns.isEmpty()) {
            throw new IOException("No visible variables to export");
        }
        
        try (java.io.PrintWriter writer = new java.io.PrintWriter(file)) {
            // Write header
            writer.println(String.join(",", visibleColumns));
            
            // Find maximum data length
            int maxLength = visibleColumns.stream()
                .mapToInt(col -> data.get(col).size())
                .max().orElse(0);
            
            // Write data rows
            for (int i = 0; i < maxLength; i++) {
                String[] row = new String[visibleColumns.size()];
                for (int j = 0; j < visibleColumns.size(); j++) {
                    String col = visibleColumns.get(j);
                    List<Double> values = data.get(col);
                    if (i < values.size()) {
                        row[j] = values.get(i).toString();
                    } else {
                        row[j] = "";
                    }
                }
                writer.println(String.join(",", row));
            }
        }
    }
    
    private List<String> getVisibleColumnNames() {
        List<String> visible = new ArrayList<>();
        for (String columnName : columnNames) {
            if (variableCheckboxes.containsKey(columnName) && 
                variableCheckboxes.get(columnName).isSelected()) {
                visible.add(columnName);
            }
        }
        return visible;
    }
    
    private void showVariableAnalysis(String variableName) {
        List<Double> originalValues = data.get(variableName);
        if (originalValues.isEmpty()) return;
        
        // Get aggregated values for analysis
        int aggWindow = variableAggregationWindows.getOrDefault(variableName, 1);
        List<Double> values = aggregateData(originalValues, aggWindow);
        
        // Calculate statistics
        double min = Collections.min(values);
        double max = Collections.max(values);
        double sum = values.stream().mapToDouble(Double::doubleValue).sum();
        double mean = sum / values.size();
        
        // Calculate standard deviation
        double variance = values.stream()
            .mapToDouble(x -> Math.pow(x - mean, 2))
            .average().orElse(0.0);
        double stdDev = Math.sqrt(variance);
        
        // Calculate derivative statistics
        List<Double> derivative = calculateDerivative(values);
        double derivMean = 0, derivStdDev = 0;
        if (!derivative.isEmpty()) {
            final double finalDerivMean = derivative.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            derivMean = finalDerivMean;
            double derivVariance = derivative.stream()
                .mapToDouble(x -> Math.pow(x - finalDerivMean, 2))
                .average().orElse(0.0);
            derivStdDev = Math.sqrt(derivVariance);
        }
        
        // Find peaks and valleys (local extrema)
        List<Integer> peaks = findPeaks(values);
        List<Integer> valleys = findValleys(values);
        
        DecimalFormat df = new DecimalFormat("#0.####");
        
        String aggregationInfo = aggWindow > 1 ? 
            String.format("Data Aggregation: %d-point averaging applied\n", aggWindow) : 
            "Data Aggregation: None (original data)\n";
        
        String analysis = String.format(
            "Analysis for: %s\n\n" +
            "%s\n" +
            "Basic Statistics:\n" +
            "• Original Data Points: %d\n" +
            "• Processed Data Points: %d\n" +
            "• Minimum: %s\n" +
            "• Maximum: %s\n" +
            "• Mean: %s\n" +
            "• Std Deviation: %s\n" +
            "• Range: %s\n\n" +
            "Derivative Statistics:\n" +
            "• Mean Rate of Change: %s\n" +
            "• Std Dev of Change: %s\n\n" +
            "Pattern Analysis:\n" +
            "• Local Peaks: %d\n" +
            "• Local Valleys: %d\n" +
            "• Trend: %s\n\n" +
            "Data Quality:\n" +
            "• Noise Reduction: %s\n" +
            "• Effective Smoothing: %s",
            variableName,
            aggregationInfo,
            originalValues.size(),
            values.size(),
            df.format(min),
            df.format(max),
            df.format(mean),
            df.format(stdDev),
            df.format(max - min),
            df.format(derivMean),
            df.format(derivStdDev),
            peaks.size(),
            valleys.size(),
            getTrendDescription(derivative),
            getNoiseReductionDescription(aggWindow),
            getSmoothingEffectiveness(originalValues, values)
        );
        
        JTextArea textArea = new JTextArea(analysis);
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(450, 400));
        
        JOptionPane.showMessageDialog(this, scrollPane, 
            "Variable Analysis - " + variableName, JOptionPane.INFORMATION_MESSAGE);
    }
    
    private String getNoiseReductionDescription(int windowSize) {
        switch (windowSize) {
            case 1: return "None (original data)";
            case 2: return "Minimal (2-point averaging)";
            case 5: return "Light (5-point averaging)";
            case 10: return "Moderate (10-point averaging)";
            case 25: return "Heavy (25-point averaging)";
            default: return String.format("Custom (%d-point averaging)", windowSize);
        }
    }
    
    private String getSmoothingEffectiveness(List<Double> original, List<Double> processed) {
        if (original.size() == processed.size()) {
            return "No aggregation applied";
        }
        
        double reductionRatio = (double) processed.size() / original.size();
        if (reductionRatio > 0.8) {
            return "Light smoothing effect";
        } else if (reductionRatio > 0.5) {
            return "Moderate smoothing effect";
        } else if (reductionRatio > 0.2) {
            return "Strong smoothing effect";
        } else {
            return "Very strong smoothing effect";
        }
    }
    
    private List<Integer> findPeaks(List<Double> values) {
        List<Integer> peaks = new ArrayList<>();
        for (int i = 1; i < values.size() - 1; i++) {
            if (values.get(i) > values.get(i - 1) && values.get(i) > values.get(i + 1)) {
                peaks.add(i);
            }
        }
        return peaks;
    }
    
    private List<Integer> findValleys(List<Double> values) {
        List<Integer> valleys = new ArrayList<>();
        for (int i = 1; i < values.size() - 1; i++) {
            if (values.get(i) < values.get(i - 1) && values.get(i) < values.get(i + 1)) {
                valleys.add(i);
            }
        }
        return valleys;
    }
    
    private String getTrendDescription(List<Double> derivative) {
        if (derivative.isEmpty()) return "No trend data";
        
        double avgChange = derivative.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double threshold = 0.001; // Adjust based on your data scale
        
        if (Math.abs(avgChange) < threshold) {
            return "Stable (no significant trend)";
        } else if (avgChange > 0) {
            return "Increasing overall";
        } else {
            return "Decreasing overall";
        }
    }
    
    private class ChartPanel extends JPanel {
        private Point mousePos = new Point();
        
        public ChartPanel() {
            // Add mouse listeners for interaction
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    mousePos = e.getPoint();
                    updateMousePosition(e.getPoint());
                }
            });
            
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        resetAllScales();
                    }
                }
            });
        }
        
        private void updateMousePosition(Point point) {
            if (data.isEmpty()) return;
            
            int margin = 50;
            int plotWidth = getWidth() - 2 * margin;
            int plotHeight = getHeight() - 2 * margin;
            
            if (point.x >= margin && point.x <= getWidth() - margin &&
                point.y >= margin && point.y <= getHeight() - margin) {
                
                // Calculate approximate data index
                double relativeX = (double)(point.x - margin) / plotWidth;
                int maxPoints = getMaxDataPoints();
                int dataIndex = (int)(relativeX * (maxPoints - 1));
                
                mousePositionLabel.setText(String.format("Mouse: X=%.2f, Index=%d", 
                    relativeX * 100, dataIndex));
            } else {
                mousePositionLabel.setText("Mouse: Outside plot area");
            }
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            
            if (data.isEmpty()) {
                drawEmptyState(g2d);
                g2d.dispose();
                return;
            }
            
            int width = getWidth();
            int height = getHeight();
            int margin = 50;
            int plotWidth = width - 2 * margin;
            int plotHeight = height - 2 * margin;
            
            // Draw background
            g2d.setColor(Color.WHITE);
            g2d.fillRect(margin, margin, plotWidth, plotHeight);
            
            // Draw grid
            drawGrid(g2d, width, height, margin, plotWidth, plotHeight);
            
            // Draw axes
            drawAxes(g2d, width, height, margin);
            
            // Draw zero line if enabled
            if (showZeroLine) {
                drawZeroLine(g2d, width, height, margin, plotWidth, plotHeight);
            }
            
            // Find visible variables
            List<String> visibleVariables = getVisibleVariables();
            if (visibleVariables.isEmpty()) {
                drawNoDataMessage(g2d, width, height);
                g2d.dispose();
                return;
            }
            
            // Plot each visible variable
            for (String columnName : visibleVariables) {
                List<Double> values = data.get(columnName);
                if (values.isEmpty()) continue;
                
                // Apply aggregation
                int aggWindow = variableAggregationWindows.getOrDefault(columnName, 1);
                List<Double> processedValues = aggregateData(values, aggWindow);
                
                Color color = variableColors.get(columnName);
                double scale = scaleSliders.get(columnName).getValue() / 100.0;
                
                // Plot original data
                plotData(g2d, processedValues, color, plotWidth, plotHeight, margin, scale, false);
                
                // Plot derivative if selected (calculate derivative from aggregated data)
                if (derivativeCheckboxes.get(columnName).isSelected()) {
                    List<Double> derivative = calculateDerivative(processedValues);
                    Color derivativeColor = new Color(color.getRed(), color.getGreen(), 
                        color.getBlue(), 150);
                    plotData(g2d, derivative, derivativeColor, plotWidth, plotHeight, margin, scale, true);
                }
            }
            
            // Draw legend
            drawLegend(g2d, width, margin);
            
            g2d.dispose();
        }
        
        private void drawEmptyState(Graphics2D g2d) {
            g2d.setColor(Color.GRAY);
            String message = "Load a CSV file to display data";
            String hint = "Use File → Load CSV or press Ctrl+O";
            
            Font titleFont = new Font(Font.SANS_SERIF, Font.BOLD, 18);
            Font hintFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
            
            g2d.setFont(titleFont);
            FontMetrics titleFm = g2d.getFontMetrics();
            int titleX = (getWidth() - titleFm.stringWidth(message)) / 2;
            int titleY = getHeight() / 2;
            g2d.drawString(message, titleX, titleY);
            
            g2d.setFont(hintFont);
            FontMetrics hintFm = g2d.getFontMetrics();
            int hintX = (getWidth() - hintFm.stringWidth(hint)) / 2;
            int hintY = titleY + 30;
            g2d.drawString(hint, hintX, hintY);
        }
        
        private void drawNoDataMessage(Graphics2D g2d, int width, int height) {
            g2d.setColor(Color.GRAY);
            String message = "No variables selected for display";
            FontMetrics fm = g2d.getFontMetrics();
            int x = (width - fm.stringWidth(message)) / 2;
            int y = height / 2;
            g2d.drawString(message, x, y);
        }
        
        private void drawGrid(Graphics2D g2d, int width, int height, int margin, 
                             int plotWidth, int plotHeight) {
            g2d.setColor(new Color(220, 220, 220));
            g2d.setStroke(new BasicStroke(0.5f));
            
            // Vertical grid lines
            for (int i = 0; i <= 10; i++) {
                int x = margin + (plotWidth * i) / 10;
                g2d.drawLine(x, margin, x, height - margin);
            }
            
            // Horizontal grid lines
            for (int i = 0; i <= 10; i++) {
                int y = margin + (plotHeight * i) / 10;
                g2d.drawLine(margin, y, width - margin, y);
            }
        }
        
        private void drawAxes(Graphics2D g2d, int width, int height, int margin) {
            g2d.setColor(Color.BLACK);
            g2d.setStroke(new BasicStroke(2));
            g2d.drawLine(margin, height - margin, width - margin, height - margin); // X-axis
            g2d.drawLine(margin, margin, margin, height - margin); // Y-axis
            
            // Add axis labels
            g2d.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            FontMetrics fm = g2d.getFontMetrics();
            
            // Y-axis label
            String yLabel = "Normalized Values";
            g2d.rotate(-Math.PI / 2);
            g2d.drawString(yLabel, -height / 2 - fm.stringWidth(yLabel) / 2, 15);
            g2d.rotate(Math.PI / 2);
            
            // X-axis label
            String xLabel = "Data Points";
            g2d.drawString(xLabel, width / 2 - fm.stringWidth(xLabel) / 2, height - 10);
        }
        
        private void drawZeroLine(Graphics2D g2d, int width, int height, int margin, 
                                 int plotWidth, int plotHeight) {
            g2d.setColor(new Color(200, 200, 200));
            g2d.setStroke(new BasicStroke(1, BasicStroke.CAP_ROUND, 
                BasicStroke.JOIN_ROUND, 0, new float[]{3, 3}, 0));
            
            int zeroY = height - margin - plotHeight / 2; // Middle of plot area
            g2d.drawLine(margin, zeroY, width - margin, zeroY);
        }
        
        private List<String> getVisibleVariables() {
            List<String> visible = new ArrayList<>();
            for (String columnName : columnNames) {
                if (variableCheckboxes.containsKey(columnName) && 
                    variableCheckboxes.get(columnName).isSelected()) {
                    visible.add(columnName);
                }
            }
            return visible;
        }
        
        private void plotData(Graphics2D g2d, List<Double> values, Color color, 
                             int plotWidth, int plotHeight, int margin, double scale, 
                             boolean isDerivative) {
            if (values.size() < 2) return;
            
            g2d.setColor(color);
            
            if (isDerivative) {
                g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, 
                    BasicStroke.JOIN_ROUND, 0, new float[]{5, 5}, 0));
            } else {
                g2d.setStroke(new BasicStroke(2));
            }
            
            // Find min and max values for scaling
            double minValue = Collections.min(values);
            double maxValue = Collections.max(values);
            double range = maxValue - minValue;
            if (range == 0) range = 1;
            
            // Apply smoothing if enabled
            List<Double> plotValues = enableSmoothing ? smoothData(values) : values;
            
            Path2D path = new Path2D.Double();
            boolean firstPoint = true;
            List<Point> dataPoints = new ArrayList<>();
            
            for (int i = 0; i < plotValues.size(); i++) {
                double normalizedValue = (plotValues.get(i) - minValue) / range;
                normalizedValue *= scale;
                
                // Clamp to reasonable bounds
                normalizedValue = Math.max(0, Math.min(normalizedValue, 5));
                
                int x = margin + (plotWidth * i) / Math.max(1, plotValues.size() - 1);
                int y = (int) (margin + plotHeight - (normalizedValue * plotHeight));
                
                // Clamp y to visible area
                y = Math.max(margin, Math.min(y, margin + plotHeight));
                
                dataPoints.add(new Point(x, y));
                
                if (firstPoint) {
                    path.moveTo(x, y);
                    firstPoint = false;
                } else {
                    path.lineTo(x, y);
                }
            }
            
            // Draw the line
            g2d.draw(path);
            
            // Draw data points if enabled
            if (showDataPoints && !isDerivative) {
                g2d.setColor(color.darker());
                for (Point p : dataPoints) {
                    g2d.fillOval(p.x - 2, p.y - 2, 4, 4);
                }
            }
        }
        
        private List<Double> smoothData(List<Double> data) {
            if (data.size() < 3) return data;
            
            List<Double> smoothed = new ArrayList<>();
            
            // Use simple 3-point moving average
            smoothed.add(data.get(0)); // First point unchanged
            
            for (int i = 1; i < data.size() - 1; i++) {
                double avg = (data.get(i - 1) + data.get(i) + data.get(i + 1)) / 3.0;
                smoothed.add(avg);
            }
            
            smoothed.add(data.get(data.size() - 1)); // Last point unchanged
            return smoothed;
        }
        
        private void drawLegend(Graphics2D g2d, int width, int margin) {
            List<String> visibleVariables = getVisibleVariables();
            if (visibleVariables.isEmpty()) return;
            
            int legendX = width - 220;
            int legendY = margin + 20;
            int lineHeight = 18;
            int legendHeight = 0;
            
            // Calculate legend height
            for (String columnName : visibleVariables) {
                legendHeight += lineHeight;
                if (derivativeCheckboxes.get(columnName).isSelected()) {
                    legendHeight += lineHeight;
                }
            }
            
            // Draw legend background
            g2d.setColor(new Color(255, 255, 255, 240));
            g2d.fillRoundRect(legendX - 10, legendY - 15, 200, legendHeight + 25, 5, 5);
            g2d.setColor(Color.BLACK);
            g2d.setStroke(new BasicStroke(1));
            g2d.drawRoundRect(legendX - 10, legendY - 15, 200, legendHeight + 25, 5, 5);
            
            // Draw legend items
            for (String columnName : visibleVariables) {
                Color color = variableColors.get(columnName);
                
                // Draw original data line
                g2d.setColor(color);
                g2d.setStroke(new BasicStroke(3));
                g2d.drawLine(legendX, legendY, legendX + 20, legendY);
                g2d.setColor(Color.BLACK);
                g2d.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
                g2d.drawString(columnName, legendX + 25, legendY + 4);
                legendY += lineHeight;
                
                // Draw derivative line if enabled
                if (derivativeCheckboxes.get(columnName).isSelected()) {
                    Color derivativeColor = new Color(color.getRed(), color.getGreen(), 
                        color.getBlue(), 150);
                    g2d.setColor(derivativeColor);
                    g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, 
                        BasicStroke.JOIN_ROUND, 0, new float[]{5, 5}, 0));
                    g2d.drawLine(legendX, legendY, legendX + 20, legendY);
                    g2d.setColor(Color.BLACK);
                    g2d.drawString(columnName + " (derivative)", legendX + 25, legendY + 4);
                    legendY += lineHeight;
                }
            }
        }
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {new CSVViewer().setVisible(true);});
    }
}