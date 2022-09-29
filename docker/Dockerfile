FROM ubuntu:22.04

ARG MY_UID

ENV DEBIAN_FRONTEND  noninteractive
# Apply available updates
# Install required tools
# Dependencies to execute Android builds
RUN apt-get update -y \
    && apt-get upgrade -y --no-install-recommends \
    && apt-get install -y --no-install-recommends \
          curl \
          expect \
          figlet \
          file \
          git \
          less \
          locales \
          npm \
          openjdk-18-jdk \
          openssh-server \
          screen \
          tzdata \
          unzip \
          vim vim-gtk3 \
          wget \
          xauth \
          zsh \
    && apt-get clean

RUN localedef -i en_US -c -f UTF-8 -A /usr/share/locale/locale.alias en_US.UTF-8
ENV LANG en_US.UTF-8


ENV STUDIO_VERSION 2021.2.1.16
WORKDIR /space
# Download and unpack Android Studio
RUN curl -L https://redirector.gvt1.com/edgedl/android/studio/ide-zips/${STUDIO_VERSION}/android-studio-${STUDIO_VERSION}-linux.tar.gz \
        | tar -zxf -
# alternative: use an already-downloaded copy when rebuilding
#ADD android-studio-${STUDIO_VERSION}-linux.tar.gz /space/
RUN ln -s /space/android-studio/bin/studio.sh /usr/local/bin/
RUN ln -s /space/home/Android/Sdk/platform-tools/adb /usr/local/bin/

ENV HOME /space/home/
# Create an entry in /etc/passwd so that Android Studio can find the data it needs (eg home directory path)
# Note: adjust the UID to match your UID on the host system
RUN useradd --home-dir $HOME --create-home \
        --shell /bin/zsh \
        -u $MY_UID \
        andbible

WORKDIR $HOME


## ------------------------------------------------------------------------
# Set up access via ssh for X-forwarding on hosts which need this
RUN mkdir /var/run/sshd
    # Tell sshd to bind to the wildcard address instead of localhost
RUN echo 'X11UseLocalhost no' >> /etc/ssh/sshd_config
    # Create a user account definition for ssh access
#RUN useradd --home-dir $HOME --create-home --shell /bin/zsh -u $MY_UID andbible
#RUN echo 'andbible:andbible' | chpasswd
## ------------------------------------------------------------------------

# Default to running sshd to allow X11-forwarding for GUI applications
#EXPOSE 22/tcp
#CMD ["/usr/sbin/sshd", "-D"]

CMD ["/usr/bin/zsh"]
