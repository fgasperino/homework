

# GRTech Homework.
#### AUTHOR: Franco Gasperino (franco.gasperino@gmail.com)

# Running the HTTP Endpoint.

  ## Start the JVM. This will start a socket REPL for local access to probe state while interacting.

     Development (Manual start)

      clj -Mapi:socket-repl

      user=> (require '[com.rate.api.dev])
      user=> (in-ns 'com.rate.api.dev)
      com.rate.api.dev=> (start)

     Production (Non-Interactive)

      clj -Aapi-prod:socket-repl

     Probe internal state (optional)

       $ telnet localhost 9999

       user=> (in-ns 'com.rate.core)
       com.rate.core=> (list-users)
       com.rate.core=> (list-sorted-by {:sort-fn :date-of-birth})

  ## Browse to one of 3 URI endpoints:

     * http://localhost:8000/records/color - Records sorted by favorite color (ascending).
     * http://localhost:8000/records/birthdate - Records sorted by date-of-birth (ascending).
     * http://localhost:8000/records/name - Records sorted by last name (descending).

  ## Submit new records.

     curl -i -X POST -H 'Content-Type: text/csv' --data-binary @resources/samples/FILENAME 'http://localhost:8000/records'

      #### FILENAME can be one of the 3 files formats:
         * input-comma.csv: 50 records in comma delimited format.
         * input-pipe.csv: 20 records in pipe delimited format.
         * input-space.csv: 20 records in space delimited format.

# Running Test Cases.

  clj -Mtest

