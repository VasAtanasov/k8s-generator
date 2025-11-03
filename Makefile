## k8s-generator — Developer Makefile
# Usage examples:
#   make build                     # Build shaded jar (skip tests)
#   make test                      # Run tests
#   make run ENGINE=kind           # Build + run with defaults (m1/pt)
#   make run MODULE=m7 TYPE=hw ENGINE=minikube OUT=hw-m7
#   make help                      # Show targets

.PHONY: help build package test run run-kind run-minikube clean

MVN ?= mvn
JAVA ?= java
JAR  ?= target/k8s-generator-1.0.0-SNAPSHOT.jar

# Smart defaults for Phase 1
MODULE ?= m1
TYPE   ?= pt
ENGINE ?= minikube
OUT    ?= $(TYPE)-$(MODULE)
ARGS   ?= --module $(MODULE) --type $(TYPE) $(ENGINE) --out $(OUT)

help:
	@echo "Available targets:"
	@echo "  build         - mvn clean package -DskipTests"
	@echo "  test          - mvn test"
	@echo "  run           - Build + run with ARGS (defaults: m1/pt/$(ENGINE))"
	@echo "                 e.g. make run ENGINE=kind"
	@echo "  run-kind      - Build + run kind with defaults"
	@echo "  run-minikube  - Build + run minikube with defaults"
	@echo "  clean         - mvn clean"
	@echo "Variables: MODULE, TYPE, ENGINE, OUT, ARGS"

build: package

package:
	$(MVN) -q clean package -DskipTests

test:
	$(MVN) -q test

run: package
	@echo "Running: $(JAR) $(ARGS)"
	$(JAVA) -jar $(JAR) $(ARGS)

run-kind:
	$(MAKE) run ENGINE=kind

run-minikube:
	$(MAKE) run ENGINE=minikube

clean:
	$(MVN) -q clean

