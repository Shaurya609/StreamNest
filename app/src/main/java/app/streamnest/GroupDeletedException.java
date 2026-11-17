package app.streamnest;
final class GroupDeletedException extends java.io.IOException {
 GroupDeletedException(){super("This sync group was deleted by the owner. Create or join a new group.");}
}
