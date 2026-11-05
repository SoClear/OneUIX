#include "traffic.h"

#include <net/if.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>
#include <linux/if_arp.h>

typedef struct {
    uint64_t tx;
    uint64_t rx;
    uint64_t interfaces;
    double time;
} Snapshot;

static Snapshot previous;

void traffic_reset(void) {
    previous = (Snapshot){0};
}

static bool count_interface(int socket_fd, const char *name) {
    struct ifreq request = {0};
    snprintf(request.ifr_name, sizeof(request.ifr_name), "%s", name);
    if (ioctl(socket_fd, SIOCGIFFLAGS, &request) < 0 ||
        !(request.ifr_flags & IFF_UP) || (request.ifr_flags & IFF_LOOPBACK)) {
        return false;
    }
    if (ioctl(socket_fd, SIOCGIFHWADDR, &request) < 0) return false;
    switch (request.ifr_hwaddr.sa_family) {
        case ARPHRD_ETHER:
            return strncmp(name, "dummy", 5) != 0 && strncmp(name, "ifb", 3) != 0 &&
                   strncmp(name, "tap", 3) != 0;
        case ARPHRD_RAWIP:
        case ARPHRD_PPP:
            return true;
        default:
            return false;
    }
}

bool traffic_sample(TrafficSpeed *speed) {
    *speed = (TrafficSpeed){0};
    FILE *file = fopen("/proc/net/dev", "re");
    if (!file) {
        traffic_reset();
        return false;
    }
    int socket_fd = socket(AF_INET, SOCK_DGRAM | SOCK_CLOEXEC, 0);
    Snapshot current = {0};
    char line[512];
    while (fgets(line, sizeof(line), file)) {
        char *name = line + strspn(line, " \t");
        char *cursor = strchr(name, ':');
        if (!cursor) continue;
        *cursor++ = '\0';
        if (!count_interface(socket_fd, name)) continue;
        current.rx += strtoull(cursor, &cursor, 10);
        for (int field = 0; field < 7; ++field) strtoull(cursor, &cursor, 10);
        current.tx += strtoull(cursor, NULL, 10);
        uint64_t hash = UINT64_C(14695981039346656037);
        for (const char *p = name; *p; ++p) hash = (hash ^ (unsigned char)*p) * UINT64_C(1099511628211);
        current.interfaces ^= hash;
    }
    fclose(file);
    close(socket_fd);
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    current.time = (double)now.tv_sec + (double)now.tv_nsec / 1e9;
    bool continuous = previous.time > 0 && current.time > previous.time &&
                      current.interfaces == previous.interfaces &&
                      current.rx >= previous.rx && current.tx >= previous.tx;
    if (continuous) {
        double elapsed = current.time - previous.time;
        speed->tx = (double)(current.tx - previous.tx) / elapsed;
        speed->rx = (double)(current.rx - previous.rx) / elapsed;
    }
    previous = current;
    return continuous;
}
