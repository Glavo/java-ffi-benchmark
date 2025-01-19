CFLAGS ?= -O2

default: library.so

library.o: library.c
	$(CC) $(CFLAGS) -Iinclude -Iinclude/linux -fPIC -c library.c

library.so: library.o
	$(CC) $(CFLAGS) -shared -fPIC library.o -o library.so

clean:
	$(RM) library.o
	$(RM) library.so