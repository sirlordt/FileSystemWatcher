package com.tomasmoreno.test;


import java.io.IOException;

import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;

import java.util.HashMap;
import java.util.Map;

public class FileSystemWatcher {

    private final WatchService watcher;
    private final Map<WatchKey,Path> keys;
    private final boolean bRecursive;
    private boolean bTrace = false;

    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        
        return (WatchEvent<T>)event;
        
    }

    /**
     * Register the given directory with the WatchService
     */
    private void register( Path dir ) throws IOException {
        
        WatchKey key = dir.register( watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY );
        
        if (bTrace) {
            
            Path prev = keys.get( key );
            
            if ( prev == null ) {
                
                System.out.format( "register: %s\n", dir );
                
            } 
            else {
                
                if ( dir.equals(prev) == false ) {
                
                    System.out.format( "update: %s -> %s\n", prev, dir );
                
                }
                
            }
            
        }
        
        
        keys.put( key, dir );
        
    }

    /**
     * Register the given directory, and all its sub-directories, with the
     * WatchService.
     */
    private void registerAll(final Path start) throws IOException {
        
        // register directory and sub-directories
        Files.walkFileTree( start, new SimpleFileVisitor<Path>() {
            
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {

                register(dir);
                
                return FileVisitResult.CONTINUE;
                
            }
            
        });
        
    }

    /**
     * Creates a WatchService and registers the given directory
     */
    FileSystemWatcher( Path dir, boolean bRecursive ) throws IOException {
        
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<WatchKey,Path>();
        this.bRecursive = bRecursive;

        if ( bRecursive ) {
            
            System.out.format("Scanning %s ...\n", dir);
            
            registerAll(dir);
            
            System.out.println("Done.");
            
        } 
        else {
            
            register( dir );
            
        }

        // enable trace after initial registration
        //this.bTrace = true;
        
    }

    /**
     * Process all events for keys queued to the watcher
     */
    void processEvents() {
        
        for ( ; ; ) {

            // wait for key to be signalled
            WatchKey key;
            try {
                
                key = watcher.take();
                
            } 
            catch ( InterruptedException x ) {
                
                return;
                
            }

            Path dir = keys.get( key );
            
            if ( dir == null ) {
                
                System.err.println( "WatchKey not recognized!!" );
                
                continue;
                
            }

            for ( WatchEvent<?> event: key.pollEvents() ) {
                
                @SuppressWarnings("rawtypes")
                WatchEvent.Kind kind = event.kind();

                // TBD - provide example of how OVERFLOW event is handled
                if ( kind == OVERFLOW ) {
                   
                    continue;
                    
                }

                // Context for directory entry event is the file name of entry
                WatchEvent<Path> ev = cast(event);
                Path name = ev.context();
                Path child = dir.resolve(name);

                // print out event
                System.out.format( "%s: %s\n", event.kind().name(), child );

                // if directory is created, and watching recursively, then
                // register it and its sub-directories
                if ( bRecursive && ( kind == ENTRY_CREATE ) ) {
                    
                    try {
                        
                        if ( Files.isDirectory( child, NOFOLLOW_LINKS ) ) {
                            
                            registerAll( child );
                            
                        }
                        
                    } 
                    catch ( IOException x ) {
                    
                        // ignore to keep sample readable
                        
                    }
                }
                
            }

            // reset key and remove from set if directory no longer accessible
            boolean bValid = key.reset();
            
            if ( bValid == false ) {
                
                keys.remove( key );

                // all directories are inaccessible
                if ( keys.isEmpty() ) {
            
                    break;
                    
                }
        
            }
            
        }
        
    }

    static void usage() {
        
        System.out.println("usage: java -jar FileSystemWatcher [-r] dir");
        
        System.exit(-1);
        
    }

    public static void main( String[] args ) throws IOException {
        
        // parse arguments
        if ( args.length == 0 || args.length > 2 )
            usage();
        
        boolean bRecursive = false;
        int intDirArg = 0;
        
        if ( args[0].equals( "-r" ) ) {
        
            if ( args.length < 2 )
                usage();
            
            bRecursive = true;
            
            intDirArg++;
            
        }

        // register directory and process its events
        Path dir = Paths.get( args[ intDirArg ] );
        
        new FileSystemWatcher( dir, bRecursive ).processEvents();
        
    }    
    
}
