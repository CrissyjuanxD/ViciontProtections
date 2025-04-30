package com.viciont.viciontprotections.database;

import com.viciont.viciontprotections.ViciontProtections;
import com.viciont.viciontprotections.models.Protection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class DatabaseManager {
    
    private final ViciontProtections plugin;
    private Connection connection;
    
    public DatabaseManager(ViciontProtections plugin) {
        this.plugin = plugin;
    }
    
    public void initialize() {
        File dataFolder = new File(plugin.getDataFolder(), plugin.getConfig().getString("database.filename", "protections.db"));
        if (!dataFolder.exists()) {
            dataFolder.getParentFile().mkdirs();
        }
        
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dataFolder);
            
            // Create tables if they don't exist
            createTables();
            
            plugin.getLogger().info("Database connection established.");
        } catch (SQLException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database:", e);
        }
    }
    
    private void createTables() {
        String protectionsTable = "CREATE TABLE IF NOT EXISTS protections ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT,"
                + "world TEXT NOT NULL,"
                + "x INTEGER NOT NULL,"
                + "y INTEGER NOT NULL,"
                + "z INTEGER NOT NULL,"
                + "size INTEGER NOT NULL,"
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ");";
        
        String ownersTable = "CREATE TABLE IF NOT EXISTS protection_owners ("
                + "protection_id INTEGER NOT NULL,"
                + "player_uuid TEXT NOT NULL,"
                + "is_primary BOOLEAN DEFAULT 0,"
                + "PRIMARY KEY (protection_id, player_uuid),"
                + "FOREIGN KEY (protection_id) REFERENCES protections (id) ON DELETE CASCADE"
                + ");";
        
        String membersTable = "CREATE TABLE IF NOT EXISTS protection_members ("
                + "protection_id INTEGER NOT NULL,"
                + "player_uuid TEXT NOT NULL,"
                + "PRIMARY KEY (protection_id, player_uuid),"
                + "FOREIGN KEY (protection_id) REFERENCES protections (id) ON DELETE CASCADE"
                + ");";
        
        try (Statement statement = connection.createStatement()) {
            statement.execute(protectionsTable);
            statement.execute(ownersTable);
            statement.execute(membersTable);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create database tables:", e);
        }
    }
    
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Database connection closed.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to close database connection:", e);
        }
    }
    
    public int createProtection(Location location, int size) {
        String sql = "INSERT INTO protections (world, x, y, z, size) VALUES (?, ?, ?, ?, ?)";
        
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, location.getWorld().getName());
            statement.setInt(2, location.getBlockX());
            statement.setInt(3, location.getBlockY());
            statement.setInt(4, location.getBlockZ());
            statement.setInt(5, size);
            
            int affectedRows = statement.executeUpdate();
            
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create protection:", e);
        }
        
        return -1;
    }
    
    public void addProtectionOwner(int protectionId, UUID playerUuid, boolean isPrimary) {
        String sql = "INSERT INTO protection_owners (protection_id, player_uuid, is_primary) VALUES (?, ?, ?)";
        
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, protectionId);
            statement.setString(2, playerUuid.toString());
            statement.setBoolean(3, isPrimary);
            
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to add protection owner:", e);
        }
    }

    public void removeProtectionOwner(int protectionId, UUID playerUuid) {
        String sql = "DELETE FROM protection_owners WHERE protection_id = ? AND player_uuid = ? AND is_primary = 0";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, protectionId);
            statement.setString(2, playerUuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to remove protection owner:", e);
        }
    }
    
    public void addProtectionMember(int protectionId, UUID playerUuid) {
        String sql = "INSERT INTO protection_members (protection_id, player_uuid) VALUES (?, ?)";
        
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, protectionId);
            statement.setString(2, playerUuid.toString());
            
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to add protection member:", e);
        }
    }
    
    public void removeProtectionMember(int protectionId, UUID playerUuid) {
        String sql = "DELETE FROM protection_members WHERE protection_id = ? AND player_uuid = ?";
        
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, protectionId);
            statement.setString(2, playerUuid.toString());
            
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to remove protection member:", e);
        }
    }
    
    public void setProtectionName(int protectionId, String name) {
        String sql = "UPDATE protections SET name = ? WHERE id = ?";
        
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setInt(2, protectionId);
            
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to set protection name:", e);
        }
    }
    
    public void deleteProtection(int protectionId) {
        String sql = "DELETE FROM protections WHERE id = ?";
        
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, protectionId);
            
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete protection:", e);
        }
    }
    
    public boolean isProtectionOwner(int protectionId, UUID playerUuid) {
        String sql = "SELECT * FROM protection_owners WHERE protection_id = ? AND player_uuid = ?";
        
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, protectionId);
            statement.setString(2, playerUuid.toString());
            
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check if player is protection owner:", e);
            return false;
        }
    }
    
    public boolean isProtectionMember(int protectionId, UUID playerUuid) {
        String sql = "SELECT * FROM protection_members WHERE protection_id = ? AND player_uuid = ?";
        
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, protectionId);
            statement.setString(2, playerUuid.toString());
            
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check if player is protection member:", e);
            return false;
        }
    }
    
    public UUID getPrimaryOwnerUuid(int protectionId) {
        String sql = "SELECT player_uuid FROM protection_owners WHERE protection_id = ? AND is_primary = 1";
        
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, protectionId);
            
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return UUID.fromString(resultSet.getString("player_uuid"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get primary owner UUID:", e);
        }
        
        return null;
    }
    
    public List<Protection> getAllProtections() {
        List<Protection> protections = new ArrayList<>();
        String sql = "SELECT * FROM protections";
        
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            
            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String name = resultSet.getString("name");
                String world = resultSet.getString("world");
                int x = resultSet.getInt("x");
                int y = resultSet.getInt("y");
                int z = resultSet.getInt("z");
                int size = resultSet.getInt("size");
                
                Location location = new Location(Bukkit.getWorld(world), x, y, z);
                Protection protection = new Protection(id, name, location, size);
                
                // Load owners
                loadProtectionOwners(protection);
                
                // Load members
                loadProtectionMembers(protection);
                
                protections.add(protection);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get all protections:", e);
        }
        
        return protections;
    }

    private void loadProtectionOwners(Protection protection) {
        String sql = "SELECT * FROM protection_owners WHERE protection_id = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, protection.getId());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID playerUuid = UUID.fromString(resultSet.getString("player_uuid"));
                    boolean isPrimary = resultSet.getBoolean("is_primary");

                    if (isPrimary) {
                        protection.setPrimaryOwner(playerUuid);
                    } else {
                        protection.addOwner(playerUuid);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load protection owners:", e);
        }
    }
    
    private void loadProtectionMembers(Protection protection) {
        String sql = "SELECT * FROM protection_members WHERE protection_id = ?";
        
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, protection.getId());
            
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID playerUuid = UUID.fromString(resultSet.getString("player_uuid"));
                    protection.addMember(playerUuid);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load protection members:", e);
        }
    }
    
    public Protection getProtectionById(int id) {
        String sql = "SELECT * FROM protections WHERE id = ?";
        
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    String name = resultSet.getString("name");
                    String world = resultSet.getString("world");
                    int x = resultSet.getInt("x");
                    int y = resultSet.getInt("y");
                    int z = resultSet.getInt("z");
                    int size = resultSet.getInt("size");
                    
                    Location location = new Location(Bukkit.getWorld(world), x, y, z);
                    Protection protection = new Protection(id, name, location, size);
                    
                    // Load owners
                    loadProtectionOwners(protection);
                    
                    // Load members
                    loadProtectionMembers(protection);
                    
                    return protection;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get protection by ID:", e);
        }
        
        return null;
    }
    
    public Protection getProtectionByName(String name) {
        String sql = "SELECT * FROM protections WHERE name = ?";
        
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    int id = resultSet.getInt("id");
                    String world = resultSet.getString("world");
                    int x = resultSet.getInt("x");
                    int y = resultSet.getInt("y");
                    int z = resultSet.getInt("z");
                    int size = resultSet.getInt("size");
                    
                    Location location = new Location(Bukkit.getWorld(world), x, y, z);
                    Protection protection = new Protection(id, name, location, size);
                    
                    // Load owners
                    loadProtectionOwners(protection);
                    
                    // Load members
                    loadProtectionMembers(protection);
                    
                    return protection;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get protection by name:", e);
        }
        
        return null;
    }
    
    public Protection getProtectionByLocation(Location blockLocation) {
        String sql = "SELECT * FROM protections";
        
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            
            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String name = resultSet.getString("name");
                String world = resultSet.getString("world");
                int x = resultSet.getInt("x");
                int y = resultSet.getInt("y");
                int z = resultSet.getInt("z");
                int size = resultSet.getInt("size");
                
                // Skip if not the same world
                if (!blockLocation.getWorld().getName().equals(world)) {
                    continue;
                }
                
                // Calculate region bounds
                int halfSize = size / 2;
                int minX = x - halfSize;
                int maxX = x + halfSize;
                int minZ = z - halfSize;
                int maxZ = z + halfSize;
                
                // Check if location is within bounds
                if (blockLocation.getBlockX() >= minX && blockLocation.getBlockX() <= maxX &&
                    blockLocation.getBlockZ() >= minZ && blockLocation.getBlockZ() <= maxZ) {
                    
                    Location location = new Location(Bukkit.getWorld(world), x, y, z);
                    Protection protection = new Protection(id, name, location, size);
                    
                    // Load owners
                    loadProtectionOwners(protection);
                    
                    // Load members
                    loadProtectionMembers(protection);
                    
                    return protection;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get protection by location:", e);
        }
        
        return null;
    }
}