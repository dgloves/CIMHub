#!/bin/bash
gridlabd -D WANT_VI_DUMP=1 test_run.glm
cat test_volt.csv
cat test_curr.csv
