# AWS Fargate Implementation

 - The proper aws fargate implementation is on branch: `deployment`

### Build Task Image

- Note, these command should be ran from the root of the project

1. `docker build --no-cache -t 923405430231.dkr.ecr.us-east-2.amazonaws.com/genetic-algorithm:latest -f aws/Dockerfile .`
2. `docker push 923405430231.dkr.ecr.us-east-2.amazonaws.com/genetic-algorithm`

