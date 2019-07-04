#include <binder/ProcessState.h>
#include <binder/IPCThreadState.h>
#include <fluid/Global.h>
#include <fluid/FLUIDService.h>

#define LOG_FLUID "FLUIDManager"

using namespace android;

int main(int argc, char *argv[])
{
	FLUIDLOG("main(): fluidmanager is starting now.");
	if (argc != 2) {
		FLUIDLOG("usage - \"fluidmanager [server / IP ADDRESS]\"");
		return 0;
	}
	if (strcmp(argv[1], "host") == 0) {
		FLUIDLOG("main(): Host side");
		FLUIDService::instantiate();
	}
	else {
		FLUIDLOG("main(): Guest side");
		FLUIDService::instantiate(argv[1]);
	}
	ProcessState::self()->startThreadPool();
	IPCThreadState::self()->joinThreadPool();
	return 0;
}
