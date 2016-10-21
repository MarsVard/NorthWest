# NorthWest #
### Simple RX library to get degrees to north 
This library tries to simplify the process of getting the degrees to north. No more messing around with sensors and calculating degrees.
Just a simple interface that returns real time updates from 0-360 degrees.

### Usage
        // initialize NorthWest with a context
        northWest = new NorthWest(this);
        
        // start listening for updates
        northWest
                .getObservable()
                .subscribe(new Subscriber<Double>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onNext(Double degrees) {
                        degreesTextView.setText(String.valueOf(degrees));
                    }
                });
                
          // dispose when activity onStop is called
          northWest.dispose();
