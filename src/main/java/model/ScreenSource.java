package model;

/**
 * Model representing a screen source (entire screen, window, or browser tab)
 * Used for screen source selection feature
 */
public class ScreenSource {
    
    /**
     * Type of screen source
     */
    public enum SourceType {
        ENTIRE_SCREEN,
        WINDOW,
        BROWSER_TAB
    }
    
    private String id;
    private SourceType type;
    private String title;
    private int width;
    private int height;
    private String appName;
    private boolean isPrimary;
    
    /**
     * Private constructor - use Builder
     */
    private ScreenSource(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.title = builder.title;
        this.width = builder.width;
        this.height = builder.height;
        this.appName = builder.appName;
        this.isPrimary = builder.isPrimary;
    }
    
    /**
     * Builder pattern for flexible ScreenSource construction
     */
    public static class Builder {
        private String id;
        private SourceType type;
        private String title;
        private int width;
        private int height;
        private String appName;
        private boolean isPrimary;
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder type(SourceType type) {
            this.type = type;
            return this;
        }
        
        public Builder title(String title) {
            this.title = title;
            return this;
        }
        
        public Builder dimensions(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }
        
        public Builder appName(String appName) {
            this.appName = appName;
            return this;
        }
        
        public Builder isPrimary(boolean isPrimary) {
            this.isPrimary = isPrimary;
            return this;
        }
        
        public ScreenSource build() {
            return new ScreenSource(this);
        }
    }
    
    // Getters
    public String getId() { 
        return id; 
    }
    
    public SourceType getType() { 
        return type; 
    }
    
    public String getTitle() { 
        return title; 
    }
    
    public int getWidth() { 
        return width; 
    }
    
    public int getHeight() { 
        return height; 
    }
    
    public String getAppName() { 
        return appName; 
    }
    
    public boolean isPrimary() { 
        return isPrimary; 
    }
    
    @Override
    public String toString() {
        return String.format("ScreenSource{id='%s', type=%s, title='%s', dimensions=%dx%d, appName='%s', isPrimary=%b}",
                           id, type, title, width, height, appName, isPrimary);
    }
}
