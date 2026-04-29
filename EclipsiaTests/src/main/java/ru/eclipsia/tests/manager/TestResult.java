package ru.eclipsia.tests.manager;

/**
 * Результат теста
 */
public class TestResult {
    
    private final String testId;
    private final String testName;
    private final boolean passed;
    private final String message;
    private final long timestamp;
    
    public TestResult(String testId, String testName, boolean passed, String message) {
        this.testId = testId;
        this.testName = testName;
        this.passed = passed;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getTestId() {
        return testId;
    }
    
    public String getTestName() {
        return testName;
    }
    
    public boolean isPassed() {
        return passed;
    }
    
    public String getMessage() {
        return message;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String toString() {
        return (passed ? "§a✓" : "§c✗") + " §7" + testName + ": §f" + message;
    }
}
