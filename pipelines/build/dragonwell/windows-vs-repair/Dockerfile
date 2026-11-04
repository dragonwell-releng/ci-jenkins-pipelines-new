ARG BASE_IMAGE
FROM ${BASE_IMAGE}
USER ContainerAdministrator
COPY upgrade-vs.ps1 C:/upgrade-vs1714.ps1
RUN powershell.exe -NoProfile -NonInteractive -File C:/upgrade-vs1714.ps1
USER ContainerUser
