import SwiftUI

struct CircularAvatarView: View {
    let displayName: String
    var profileImageData: Data? = nil
    var size: CGFloat = 52

    var body: some View {
        if let imageData = profileImageData, let uiImage = UIImage(data: imageData) {
            Image(uiImage: uiImage)
                .resizable()
                .scaledToFill()
                .frame(width: size, height: size)
                .clipShape(Circle())
        } else {
            Circle()
                .fill(FestivalTheme.mainGradientDiagonal)
                .frame(width: size, height: size)
                .overlay(
                    Text(String(displayName.prefix(1)).uppercased())
                        .font(.system(size: size * 0.4, weight: .heavy))
                        .foregroundColor(.white)
                )
        }
    }
}
