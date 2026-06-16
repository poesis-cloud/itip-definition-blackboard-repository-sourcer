SHELL := /bin/bash
.EXPORT_ALL_VARIABLES:

.PHONY: dev-check dev-up dev-down deploy-check prod-deploy package-helm run-api test verify

-include .env
-include .env.dev

NAMESPACE ?= sie
DEPLOY_ENV ?= preprod
RELEASE ?= itip-definition-blackboard-repository-sourcer

SOURCER_CHART ?= ./ops/helm
SOURCER_ENV_FILE ?= $(SOURCER_CHART)/environments/$(DEPLOY_ENV)/values.yaml

dev-check:
	@command -v kubectl >/dev/null 2>&1 || { echo "Missing required command: kubectl"; exit 1; }
	@command -v helm >/dev/null 2>&1 || { echo "Missing required command: helm"; exit 1; }
	@kubectl config current-context >/dev/null 2>&1 || { echo "No active Kubernetes context. Configure kubeconfig first."; exit 1; }
	@kubectl get ns >/dev/null 2>&1 || { echo "Cannot reach Kubernetes API with current context."; exit 1; }
	@test -d "$(SOURCER_CHART)" || { echo "Missing chart directory: $(SOURCER_CHART)"; exit 1; }
	@test -f "$(SOURCER_CHART)/environments/dev/values.yaml" || { echo "Missing dev values file: $(SOURCER_CHART)/environments/dev/values.yaml"; exit 1; }
	@echo "dev-check passed"

deploy-check:
	@command -v kubectl >/dev/null 2>&1 || { echo "Missing required command: kubectl"; exit 1; }
	@command -v helm >/dev/null 2>&1 || { echo "Missing required command: helm"; exit 1; }
	@kubectl config current-context >/dev/null 2>&1 || { echo "No active Kubernetes context. Configure kubeconfig first."; exit 1; }
	@kubectl get ns >/dev/null 2>&1 || { echo "Cannot reach Kubernetes API with current context."; exit 1; }
	@test -d "$(SOURCER_CHART)" || { echo "Missing chart directory: $(SOURCER_CHART)"; exit 1; }
	@test -f "$(SOURCER_ENV_FILE)" || { echo "Missing environment values file: $(SOURCER_ENV_FILE)"; exit 1; }
	@: "$${IMAGE_REPOSITORY:?Missing IMAGE_REPOSITORY in environment}"
	@: "$${IMAGE_TAG:?Missing IMAGE_TAG in environment}"
	@: "$${OAUTH2_CLIENT_SECRET:?Missing OAUTH2_CLIENT_SECRET in environment}"
	@echo "deploy-check passed"

dev-up:
	@$(MAKE) dev-check
	kubectl get ns $(NAMESPACE) >/dev/null 2>&1 || kubectl create ns $(NAMESPACE) >/dev/null
	helm upgrade --install $(RELEASE) $(SOURCER_CHART) -n $(NAMESPACE) --create-namespace --wait --timeout 5m0s \
		-f $(SOURCER_CHART)/environments/dev/values.yaml \
		--set-string secrets.OAUTH2_CLIENT_SECRET="$${OAUTH2_CLIENT_SECRET:-dummy}"

dev-down:
	helm uninstall $(RELEASE) -n $(NAMESPACE) || true

run-api:
	BLACKBOARD_BASE_URL="$(BLACKBOARD_BASE_URL)" \
	OIDC_ISSUER_URI="$(OIDC_ISSUER_URI)" \
	mvn spring-boot:run

prod-deploy:
	@$(MAKE) deploy-check
	helm upgrade --install $(RELEASE) $(SOURCER_CHART) -n $(NAMESPACE) --create-namespace --wait --timeout 10m0s \
		-f $(SOURCER_ENV_FILE) \
		--set image.repository="$${IMAGE_REPOSITORY}" \
		--set image.tag="$${IMAGE_TAG}" \
		--set-string secrets.OAUTH2_CLIENT_SECRET="$${OAUTH2_CLIENT_SECRET}"

package-helm:
	@command -v helm >/dev/null 2>&1 || { echo "Missing required command: helm"; exit 1; }
	@test -d "$(SOURCER_CHART)" || { echo "Missing chart directory: $(SOURCER_CHART)"; exit 1; }
	helm package $(SOURCER_CHART)

test:
	mvn test

verify:
	mvn verify
