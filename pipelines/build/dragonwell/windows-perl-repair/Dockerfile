ARG BASE_IMAGE
FROM ${BASE_IMAGE}
USER ContainerAdministrator
COPY repair-perl.ps1 C:/dragonwell-repair-perl.ps1
RUN powershell.exe -NoProfile -NonInteractive -File C:/dragonwell-repair-perl.ps1
USER ContainerUser
RUN C:\cygwin64\bin\perl.exe -MEncode -MMIME::Base64 -MList::Util -MText::Wrap -MTime::Local -MDigest::SHA -MGetopt::Std -e 1
