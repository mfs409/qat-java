package com.intel.qat;

public class QatZipper{

    /**
     * 
     */
    public ZipperBackend backend;


    /** A class that represents a cleaner action for a QAT session. */
  static class BackendCleaner implements Runnable {
    private ZipperBackend backend;

    /** Creates a new cleaner object that cleans up the specified session. */
    public QatCleaner(ZipperBackend backend) {
      this.backend = backend;
    }

    @Override
    public void run() {
      if(backend != null){
        backend.teardown();
      }
    }
  }

}