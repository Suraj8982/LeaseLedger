#!/bin/sh
# Runs inside the LocalStack container at startup. "awslocal" is the AWS CLI pointed at the emulator.
awslocal s3 mb s3://leaseledger-local

# Browsers refuse cross-origin uploads unless the bucket allows them. The React app
# (milestone 6) will PUT photos to this bucket from http://localhost:5173.
awslocal s3api put-bucket-cors --bucket leaseledger-local --cors-configuration '{
  "CORSRules": [{
    "AllowedOrigins": ["http://localhost:5173"],
    "AllowedMethods": ["PUT", "GET"],
    "AllowedHeaders": ["*"]
  }]
}'
